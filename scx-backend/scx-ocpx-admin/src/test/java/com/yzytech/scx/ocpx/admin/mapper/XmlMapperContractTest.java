package com.yzytech.scx.ocpx.admin.mapper;

import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;
import com.yzytech.scx.ocpx.admin.dto.SummaryReportQuery;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class XmlMapperContractTest {

	@Test
	void springRegistersBasicAndXmlMappersWithoutDatabaseConnections() {
		try (var context = XmlMapperTestSupport.mapperContext("com.yzytech.scx.ocpx.admin.mapper")) {
			assertThat(context.getBean(OcpxAdvertiserMapper.class)).isNotNull();
			assertThat(context.getBean(OcpxMonitorTaskMapper.class)).isNotNull();
			assertThat(context.getBean(RtaJobsSqlMapper.class)).isNotNull();
			assertThat(context.getBean(OcpxUnifiedReportSqlMapper.class)).isNotNull();
		}
	}

	@Test
	void allBusinessMapperXmlFilesLoadAndEveryCustomMethodIsBound() throws Exception {
		var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*Mapper.xml");
		int checked = 0;
		for (var resource : resources) {
			String xml = resource.getContentAsString(StandardCharsets.UTF_8);
			var namespace = Pattern.compile("<mapper namespace=\"([^\"]+)\"").matcher(xml);
			assertThat(namespace.find()).isTrue();
			if (!namespace.group(1).startsWith("com.yzytech.scx.ocpx.admin.mapper."))
				continue;
			Class<?> type = Class.forName(namespace.group(1));
			var configuration = XmlMapperTestSupport.configuration(type);
			for (var method : type.getDeclaredMethods()) {
				if (method.isDefault() || java.lang.reflect.Modifier.isStatic(method.getModifiers()))
					continue;
				assertThat(configuration.hasStatement(type.getName() + "." + method.getName())).as(method.toString())
					.isTrue();
			}
			checked++;
		}
		assertThat(checked).isGreaterThanOrEqualTo(15);
	}

	@Test
	void emptyCollectionsCannotProduceAnUnboundedQueryOrDelete() {
		assertThat(sql(OcpxAdvertiserAccountSqlMapper.class, "selectBalancesByIds", Map.of("ids", List.of())))
			.contains("1=0")
			.doesNotContain("IN ( )");
		assertThat(
				sql(OcpxCreativeSqlMapper.class, "deleteBindings", Map.of("creativeId", "creative", "ids", Set.of())))
			.contains("creative_id=?", "1=0");
		var bound = XmlMapperTestSupport.configuration(OcpxAdvertiserAccountSqlMapper.class)
			.getMappedStatement(OcpxAdvertiserAccountSqlMapper.class.getName() + ".selectBalancesByIds")
			.getBoundSql(Map.of("ids", List.of("one", "two")));
		assertThat(bound.getParameterMappings()).hasSize(2);
		assertThat(bound.getSql()).doesNotContain("one", "two");
	}

	@Test
	void rawSortInputNeverBecomesSqlAndLockReadsBypassCaches() {
		SummaryReportQuery query = new SummaryReportQuery();
		query.setDateType(0);
		query.setAsc("reportTime; DROP TABLE injected");
		var params = new HashMap<String, Object>();
		params.put("q", query);
		params.put("startHour", 2026090800);
		params.put("endHour", 2026090823);
		params.put("paged", true);
		params.put("limit", 10);
		params.put("offset", 0);
		assertThat(sql(OcpxUnifiedReportSqlMapper.class, "selectSummary", params))
			.contains("ORDER BY reportTime DESC", "LIMIT ? OFFSET ?")
			.doesNotContain("DROP", "injected");
		var statement = XmlMapperTestSupport.configuration(OcpxAdvertiserAccountSqlMapper.class)
			.getMappedStatement(OcpxAdvertiserAccountSqlMapper.class.getName() + ".selectLockBalanceAdvertiserAccount");
		assertThat(statement.isUseCache()).isFalse();
		assertThat(statement.isFlushCacheRequired()).isTrue();
	}

	private String sql(Class<?> type, String method, Object params) {
		return XmlMapperTestSupport.configuration(type)
			.getMappedStatement(type.getName() + "." + method)
			.getBoundSql(params)
			.getSql()
			.replaceAll("\\s+", " ");
	}

}
