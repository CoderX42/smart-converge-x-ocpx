package com.yzytech.scx.ocpx.persistence.mapper;

import com.yzytech.scx.ocpx.ScxOcpxEngineApplication;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class EngineXmlMapperContractTest {

	@Test
	void springRegistersTheProductionEngineMapperPackagesWithoutDatabaseConnections() {
		try (var context = XmlMapperTestSupport
			.mapperContext(ScxOcpxEngineApplication.class.getAnnotation(MapperScan.class).value())) {
			assertThat(context.getBean(OcpxEventFactSqlMapper.class)).isNotNull();
			assertThat(context.getBean(RelaySqlMapper.class)).isNotNull();
			assertThat(context.getBean(com.yzytech.scx.ocpx.configuration.mapper.OcpxAdvertiserConfigMapper.class))
				.isNotNull();
		}
	}

	@Test
	void engineScansAndLoadsEveryPersistenceMapper() throws Exception {
		assertThat(ScxOcpxEngineApplication.class.getAnnotation(MapperScan.class).value())
			.contains("com.yzytech.scx.ocpx.persistence.mapper");
		int checked = 0;
		for (var resource : new PathMatchingResourcePatternResolver()
			.getResources("classpath*:mapper/*SqlMapper.xml")) {
			var namespace = Pattern.compile("<mapper namespace=\"([^\"]+)\"")
				.matcher(resource.getContentAsString(StandardCharsets.UTF_8));
			assertThat(namespace.find()).isTrue();
			Class<?> type = Class.forName(namespace.group(1));
			var configuration = XmlMapperTestSupport.configuration(type);
			for (var method : type.getDeclaredMethods())
				assertThat(configuration.hasStatement(type.getName() + "." + method.getName())).isTrue();
			checked++;
		}
		assertThat(checked).isEqualTo(6);
	}

	@Test
	void retentionCursorAndRtaColumnSelectionUseBoundParameters() {
		var configuration = XmlMapperTestSupport.configuration(FlowLogRetentionSqlMapper.class);
		var statement = configuration
			.getMappedStatement(FlowLogRetentionSqlMapper.class.getName() + ".selectExpiredPreviews");
		var params = new HashMap<String, Object>();
		params.put("eventTime", null);
		params.put("id", null);
		params.put("limit", 1000);
		assertThat(statement.getBoundSql(params).getParameterMappings()).hasSize(1);
		params.put("eventTime", java.time.LocalDateTime.of(2026, 9, 1, 0, 0));
		params.put("id", "cursor");
		assertThat(statement.getBoundSql(params).getParameterMappings()).hasSize(4);
		var relay = XmlMapperTestSupport.configuration(RelaySqlMapper.class)
			.getMappedStatement(RelaySqlMapper.class.getName() + ".selectUpstream");
		assertThat(relay.getBoundSql(Map.of("column", "access_code", "value", "secret-ref")).getSql())
			.contains("u.access_code=?")
			.doesNotContain("secret-ref");
		assertThat(relay.getBoundSql(Map.of("column", "id OR 1=1", "value", "value")).getSql()).contains("1=0")
			.doesNotContain("OR 1=1");
	}

}
