package com.yzytech.scx.ocpx.rta.application;

import com.yzytech.scx.ocpx.rta.adapter.RtaMockDecisionAdapter;
import com.yzytech.scx.ocpx.rta.admission.RtaAdmissionStore;
import com.yzytech.scx.ocpx.rta.config.RtaProperties;
import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.exception.RtaConflictException;
import com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionRequest;
import com.yzytech.scx.ocpx.rta.stream.RtaStreamPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



class RtaDecisionServiceTest {

	@Test
	void sameRequestIdWithDifferentContentCannotOverwriteCachedDecision() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RtaAdmissionStore admission = mock(RtaAdmissionStore.class);
		RtaStreamPublisher stream = mock(RtaStreamPublisher.class);
		RtaProperties properties = new RtaProperties();
		properties.setEnabled(true);
		properties.setMockEnabled(true);
		RtaDecisionService service = new RtaDecisionService(jdbc, properties, admission, stream,
				List.of(new RtaMockDecisionAdapter()));
		stubEnabledTask(jdbc);
		when(admission.acquire("task-1", "req-1", 30_000)).thenReturn("lock-token");
		when(admission.find("task-1", "req-1")).thenReturn(new RtaAdmissionStore.Admission("req-1", "old-hash",
				"adv-1", "link-1", "media-1", "task-1", "profile-1", RtaDecision.ALLOW, "MOCK_ALLOW",
				List.of("ext-1"), Instant.now().plusSeconds(300).getEpochSecond()));

		assertThatThrownBy(() -> service.decide(request())).isInstanceOf(RtaConflictException.class);
		verify(admission).release("task-1", "req-1", "lock-token");
	}

	@Test
	void redisLockFailureDoesNotCallAdapterOrPublishAllow() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RtaAdmissionStore admission = mock(RtaAdmissionStore.class);
		RtaStreamPublisher stream = mock(RtaStreamPublisher.class);
		RtaProperties properties = new RtaProperties();
		properties.setEnabled(true);
		properties.setMockEnabled(true);
		RtaDecisionService service = new RtaDecisionService(jdbc, properties, admission, stream,
				List.of(new RtaMockDecisionAdapter()));
		stubEnabledTask(jdbc);
		when(admission.acquire("task-1", "req-1", 30_000)).thenThrow(new RtaInfrastructureException("redis down"));

		assertThatThrownBy(() -> service.decide(request())).isInstanceOf(RtaInfrastructureException.class);
		verify(stream, org.mockito.Mockito.never()).publish(any());
	}

	private void stubEnabledTask(JdbcTemplate jdbc) {
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			var mapper = (RowMapper<?>) invocation.getArgument(1);
			var resultSet = mock(java.sql.ResultSet.class);
			when(resultSet.getString("id")).thenReturn("task-1");
			when(resultSet.getString("advertiser_id")).thenReturn("adv-1");
			when(resultSet.getString("promotion_link_id")).thenReturn("link-1");
			when(resultSet.getString("media_id")).thenReturn("media-1");
			when(resultSet.getBoolean("rta_enabled")).thenReturn(true);
			return List.of(mapper.mapRow(resultSet, 0));
		});
	}

	private RtaDecisionRequest request() {
		RtaDecisionRequest request = new RtaDecisionRequest();
		request.setRequestId("req-1");
		request.setAdvertiserId("adv-1");
		request.setPromotionLinkId("link-1");
		request.setMediaId("media-1");
		request.setMonitorTaskId("task-1");
		return request;
	}

}
