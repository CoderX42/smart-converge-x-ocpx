package com.yzytech.scx.ocpx.testing;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.*;
import org.mockito.Mockito;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/**
 * Loads production XML for SQL-focused service regression fixtures. A mocked JDBC fixture
 * is exposed as a Mockito Mapper; real data sources use SqlSessionTemplate. No SQL text
 * is duplicated in this adapter. New service tests can mock Mapper methods directly;
 * existing SQL/argument assertions continue to cover the XML.
 */
public final class XmlMapperTestSupport {

	private XmlMapperTestSupport() {
	}

	public static org.springframework.context.support.GenericApplicationContext mapperContext(String... packages) {
		var context = new org.springframework.context.support.GenericApplicationContext();
		javax.sql.DataSource dataSource = Mockito.mock(javax.sql.DataSource.class);
		context.registerBean("sqlSessionFactory", org.apache.ibatis.session.SqlSessionFactory.class, () -> {
			try {
				var factory = new MybatisSqlSessionFactoryBean();
				factory.setDataSource(dataSource);
				var configuration = new MybatisConfiguration();
				configuration.setMapUnderscoreToCamelCase(true);
				factory.setConfiguration(configuration);
				factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
					.getResources("classpath*:mapper/*Mapper.xml"));
				return factory.getObject();
			}
			catch (Exception error) {
				throw new IllegalStateException(error);
			}
		});
		var scanner = new org.mybatis.spring.mapper.ClassPathMapperScanner(context);
		scanner.setSqlSessionFactoryBeanName("sqlSessionFactory");
		scanner.registerFilters();
		scanner.scan(packages);
		context.refresh();
		Mockito.verifyNoInteractions(dataSource);
		return context;
	}

	public static MybatisConfiguration configuration(Class<?> type) {
		try {
			var configuration = new MybatisConfiguration();
			configuration.setMapUnderscoreToCamelCase(true);
			configuration.setCallSettersOnNulls(true);
			configuration.setShrinkWhitespacesInSql(true);
			configuration.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
			String resource = "mapper/" + type.getSimpleName() + ".xml";
			try (var input = new ClassPathResource(resource).getInputStream()) {
				new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
			}
			return configuration;
		}
		catch (Exception error) {
			throw new IllegalStateException("Cannot load " + type.getName(), error);
		}
	}

	public static <T> T mapper(Class<T> type, JdbcTemplate fixture) {
		var configuration = configuration(type);
		if (!Mockito.mockingDetails(fixture).isMock()) {
			try {
				var factory = new MybatisSqlSessionFactoryBean();
				factory.setDataSource(Objects.requireNonNull(fixture.getDataSource()));
				configuration.addInterceptor(
						new com.yzytech.scx.common.myabtis.config.MybatisPlusConfig().mybatisPlusInterceptor());
				factory.setConfiguration(configuration);
				return new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(type);
			}
			catch (Exception error) {
				throw new IllegalStateException(error);
			}
		}
		return Mockito.mock(type, invocation -> {
			Method method = invocation.getMethod();
			if (method.getDeclaringClass() == Object.class)
				return Mockito.RETURNS_DEFAULTS.answer(invocation);
			Object parameter = new ParamNameResolver(configuration, method).getNamedParams(invocation.getArguments());
			var statement = configuration.getMappedStatement(type.getName() + "." + method.getName());
			BoundSql bound = statement.getBoundSql(parameter);
			Object[] values = bound.getParameterMappings().stream().map(mapping -> {
				String property = mapping.getProperty();
				if (bound.hasAdditionalParameter(property))
					return bound.getAdditionalParameter(property);
				return configuration.newMetaObject(parameter).getValue(property);
			}).toArray();
			String sql = bound.getSql().replaceAll("\\s+", " ").replaceAll(",\\s+", ",").trim();
			if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
				return values.length == 0 ? fixture.update(sql) : fixture.update(sql, values);
			}
			ResultMap result = statement.getResultMaps().get(0);
			Class<?> rowType = result.getType();
			if (Map.class.isAssignableFrom(rowType))
				return fixture.queryForList(sql, values);
			RowMapper<Object> rowMapper = (rs, n) -> row(result, rs, sql);
			if (!List.class.isAssignableFrom(method.getReturnType())) {
				if (!rowType.isRecord())
					return values.length == 0 && rowType == Integer.class ? fixture.queryForObject(sql, rowType)
							: fixture.queryForObject(sql, rowType, values);
				return fixture.queryForObject(sql, rowMapper, values);
			}
			List<?> rows;
			if (method.getName().equals("aggregate")) {
				rows = fixture.query(sql, (ResultSetExtractor<List<Object>>) rs -> {
					List<Object> items = new ArrayList<>();
					while (rs.next())
						items.add(rowMapper.mapRow(rs, items.size()));
					return items;
				}, values);
			}
			else if (!rowType.isRecord() && !result.getConstructorResultMappings().isEmpty()) {
				rows = fixture.query(sql, rowMapper, values);
			}
			else if (rowType == String.class) {
				rows = fixture.queryForList(sql, String.class, values);
			}
			else {
				rows = fixture.query(sql, rowMapper, values);
			}
			if (rows == null)
				return List.of();
			List<Object> converted = new ArrayList<>();
			for (Object value : rows)
				converted.add(project(rowType, value));
			return converted;
		});
	}

	private static Object row(ResultMap result, ResultSet rs, String sql) throws SQLException {
		try {
			Class<?> type = result.getType();
			var mappings = result.getConstructorResultMappings();
			if (mappings.isEmpty()) {
				// Existing option DTOs use a two-string constructor.
				return type.getDeclaredConstructor(String.class, String.class)
					.newInstance(rs.getString(1), rs.getString(2));
			}
			Object[] values = new Object[mappings.size()];
			Class<?>[] types = new Class<?>[mappings.size()];
			for (int i = 0; i < mappings.size(); i++) {
				ResultMapping mapping = mappings.get(i);
				types[i] = mapping.getJavaType();
				String getter = getter(types[i]);
				String column = mapping.getColumn();
				int position = columnPosition(sql, column, i + 1);
				boolean named = Mockito.mockingDetails(rs)
					.getStubbings()
					.stream()
					.anyMatch(stub -> stub.getInvocation().getMethod().getName().equals(getter)
							&& Arrays.equals(stub.getInvocation().getArguments(), new Object[] { column }));
				Object key = named ? column : position;
				Method read = ResultSet.class.getMethod(getter, named ? String.class : int.class);
				values[i] = read.invoke(rs, key);
			}
			Constructor<?> constructor = type.getDeclaredConstructor(types);
			constructor.setAccessible(true);
			return constructor.newInstance(values);
		}
		catch (ReflectiveOperationException error) {
			throw new SQLException("Cannot map XML result " + result.getId(), error);
		}
	}

	private static String getter(Class<?> type) {
		if (type == String.class)
			return "getString";
		if (type == long.class)
			return "getLong";
		if (type == int.class)
			return "getInt";
		if (type == boolean.class)
			return "getBoolean";
		if (type == java.math.BigDecimal.class)
			return "getBigDecimal";
		if (type == Timestamp.class)
			return "getTimestamp";
		if (type == Double.class)
			return "getDouble";
		return "getObject";
	}

	private static int columnPosition(String sql, String column, int fallback) {
		int start = sql.toUpperCase(Locale.ROOT).indexOf("SELECT ") + 7, depth = 0, ordinal = 1, left = start;
		boolean quote = false;
		for (int i = start; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c == '\'')
				quote = !quote;
			if (quote)
				continue;
			if (c == '(')
				depth++;
			if (c == ')')
				depth--;
			boolean end = depth == 0 && sql.regionMatches(true, i, " FROM ", 0, 6);
			if (depth == 0 && c == ',' || end) {
				String expression = sql.substring(left, i).trim();
				String label = expression.replaceFirst("(?is)^.*\\s+AS\\s+", "").replaceFirst("^\\w+\\.", "");
				if (label.equalsIgnoreCase(column))
					return ordinal;
				ordinal++;
				left = i + 1;
				if (end)
					break;
			}
		}
		return fallback;
	}

	private static Object project(Class<?> target, Object value) throws ReflectiveOperationException {
		if (value == null || target.isInstance(value))
			return value;
		if (!target.isRecord())
			return value;
		RecordComponent[] fields = target.getRecordComponents();
		Object[] args = new Object[fields.length];
		if (fields.length == 1)
			args[0] = value;
		else {
			// Legacy fixtures may supply the previous view object directly.
			List<Field> source = Arrays.stream(value.getClass().getDeclaredFields())
				.filter(f -> !Modifier.isStatic(f.getModifiers()))
				.toList();
			if (source.size() != fields.length)
				throw new IllegalArgumentException("Fixture projection mismatch: " + target);
			for (int i = 0; i < fields.length; i++) {
				source.get(i).setAccessible(true);
				args[i] = source.get(i).get(value);
			}
		}
		for (int i = 0; i < fields.length; i++)
			if (fields[i].getType() == Timestamp.class && args[i] instanceof java.time.LocalDateTime time)
				args[i] = Timestamp.valueOf(time);
		return target
			.getDeclaredConstructor(Arrays.stream(fields).map(RecordComponent::getType).toArray(Class<?>[]::new))
			.newInstance(args);
	}

}
