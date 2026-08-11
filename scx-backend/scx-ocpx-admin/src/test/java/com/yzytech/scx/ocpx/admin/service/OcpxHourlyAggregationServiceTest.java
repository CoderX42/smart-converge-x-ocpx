package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.config.OcpxReportingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxHourlyAggregationServiceTest {

	private final NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	private final OcpxReportingProperties properties = new OcpxReportingProperties();

	private final OcpxHourlyAggregationService service = new OcpxHourlyAggregationService(named, properties);

	@Test
	void disabledWorkerIsFailClosed() {
		service.scheduledAggregate();
		verify(named, never()).getJdbcTemplate();
	}

	@Test
	void oneTransactionAggregatesThenAcknowledgesTheSameContributionIds() {
		when(named.getJdbcTemplate()).thenReturn(jdbc);
		when(jdbc.queryForList(contains("aggregated_at IS NULL"), org.mockito.ArgumentMatchers.eq(Long.class),
				anyInt()))
			.thenReturn(List.of(11L, 12L));
		int processed = service.aggregateNextBatch("authorised-test-backfill");
		assertThat(processed).isEqualTo(2);
		verify(named).update(contains("ON DUPLICATE KEY UPDATE"), any(MapSqlParameterSource.class));
		verify(named).update(contains("SET aggregated_at=NOW(3)"), any(MapSqlParameterSource.class));
		verify(jdbc).update(contains("last_sequence_no=?"), any(), any(), any(), any());
	}

}
