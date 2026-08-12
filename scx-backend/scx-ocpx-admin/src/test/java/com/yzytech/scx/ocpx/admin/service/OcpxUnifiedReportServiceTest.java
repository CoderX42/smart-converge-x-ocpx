package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.UnifiedReportQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcpxUnifiedReportServiceTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void readsTheFinalLegacyReportAndKeepsAllMetricsAndPriceScale() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString(1)).thenReturn("2026081201");
		when(resultSet.getString(2)).thenReturn("2026-08-12 01:00");
		for (int index = 3; index <= 17; index++) {
			when(resultSet.getLong(index)).thenReturn((long) index - 2);
		}
		when(resultSet.getBigDecimal(18)).thenReturn(BigDecimal.valueOf(20_000_000));
		when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
			.thenAnswer(invocation -> ((ResultSetExtractor) invocation.getArgument(1)).extractData(resultSet));
		OcpxUnifiedReportService service = new OcpxUnifiedReportService(jdbcTemplate);
		UnifiedReportQuery query = new UnifiedReportQuery(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12), "TIME",
				"advertiser-id", "link-id", "media-id", "task-id", 1, 20);

		var page = service.query(query);

		assertThat(page.total()).isEqualTo(1);
		assertThat(page.records()).hasSize(1);
		assertThat(page.records().get(0).metrics()).hasSize(16);
		assertThat(page.records().get(0).metrics().get("SHOW").count()).isEqualTo(1);
		assertThat(page.records().get(0).metrics().get("FINISH_THE_ORDER").count()).isEqualTo(15);
		assertThat(page.records().get(0).metrics().get("BASE_PRICE").amount())
			.isEqualByComparingTo(new BigDecimal("2.00"));
		assertThat(page.summary().keySet()).containsAll(List.of("SHOW", "UNIQUE_SHOW", "CLICK", "UNIQUE_CLICK",
				"ACTIVATE", "EVOKE", "LOGIN", "REGISTER", "PAYMENT", "CUSTOMER_ACQUISITION", "NEXT_STAY", "VISITS",
				"AUTHENTICATION", "PLACE_ORDER", "FINISH_THE_ORDER", "BASE_PRICE"));
	}

}
