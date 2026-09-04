package com.yzytech.scx.ocpx.rta.persistence;

import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



class RtaDecisionFactPersistenceTest {

	@Test
	void writesFactAndHourlyMetricWithOneMetricRowId() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		new RtaDecisionFactPersistence(jdbc).persist(event());

		var sql = org.mockito.ArgumentCaptor.forClass(String.class);
		var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, times(2)).update(sql.capture(), args.capture());
		assertThat(sql.getAllValues().get(0)).contains("scx_ocpx_rta_decision_fact");
		assertThat(sql.getAllValues().get(1)).contains("scx_ocpx_rta_hourly_metric");
		assertThat(args.getAllValues().get(1)).hasSize(14);
	}

	@Test
	void duplicateFactDoesNotIncrementHourlyMetricAgain() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));
		new RtaDecisionFactPersistence(jdbc).persist(event());

		verify(jdbc, times(1)).update(anyString(), any(Object[].class));
	}

	private RtaDecisionEvent event() {
		LocalDateTime received = LocalDateTime.of(2026, 9, 4, 13, 0);
		return new RtaDecisionEvent("req-1", "a".repeat(64), "adv-1", "link-1", "media-1", "task-1",
				"profile-1", "MOCK", RtaDecision.ALLOW, "SUCCESS", "MOCK_ALLOW", 2, List.of("ext-1"), received,
				received.plusNanos(2_000_000));
	}

}
