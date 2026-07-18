package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.RtaEndpointMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.tracking.dto.RtaDecisionRequest;
import com.yzytech.scx.tracking.dto.RtaDecisionResponse;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import com.yzytech.scx.tracking.mapper.RtaDecisionLogMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RtaDecisionServiceTest {

	private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);

	private final RtaEndpointMapper endpointMapper = mock(RtaEndpointMapper.class);

	private final RtaDecisionLogMapper logMapper = mock(RtaDecisionLogMapper.class);

	private final RtaTransport transport = mock(RtaTransport.class);

	private final RtaDecisionService service = new RtaDecisionService(taskMapper, endpointMapper, logMapper, transport,
			new ObjectMapper());

	@Test
	void callsActiveTaskEndpointAndPersistsAllowDecision() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setMediaId("media-1");
		task.setStatus("ACTIVE");
		task.setRtaEnabled(true);
		task.setRtaEndpointId("rta-1");
		RtaEndpoint endpoint = new RtaEndpoint();
		endpoint.setId("rta-1");
		endpoint.setMediaId("media-1");
		endpoint.setStatus("ACTIVE");
		endpoint.setEndpointUrl("http://callback-mock:8080/rta");
		endpoint.setRtaIdentifier("sandbox-rta");
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(endpointMapper.selectById("rta-1")).thenReturn(endpoint);
		when(transport.decide(any(), any())).thenReturn("{\"decision\":\"ALLOW\"}");

		RtaDecisionResponse response = service.decide(request());

		assertThat(response.decision()).isEqualTo("ALLOW");
		assertThat(response.duplicateDelivery()).isFalse();
		verify(logMapper).insert(any(RtaDecisionLog.class));
		verify(logMapper).updateById(any(RtaDecisionLog.class));
	}

	@Test
	void failsClosedWhenEndpointCannotBeReached() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setMediaId("media-1");
		task.setStatus("ACTIVE");
		task.setRtaEnabled(true);
		task.setRtaEndpointId("rta-1");
		RtaEndpoint endpoint = new RtaEndpoint();
		endpoint.setId("rta-1");
		endpoint.setMediaId("media-1");
		endpoint.setStatus("ACTIVE");
		endpoint.setEndpointUrl("http://callback-mock:8080/rta");
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(endpointMapper.selectById("rta-1")).thenReturn(endpoint);
		when(transport.decide(any(), any())).thenThrow(new RtaTransportException("NETWORK_ERROR", "down"));

		RtaDecisionResponse response = service.decide(request());

		assertThat(response.decision()).isEqualTo("DENY");
		assertThat(response.reasonCode()).isEqualTo("NETWORK_ERROR");
	}

	private RtaDecisionRequest request() {
		RtaDecisionRequest request = new RtaDecisionRequest();
		request.setSchemaVersion("1.0");
		request.setRequestId("rta-request-1");
		request.setTaskCode("TASK-1");
		request.setDeviceKeyHash("device-hash");
		request.setAttributes(Map.of("campaignId", "c-1"));
		return request;
	}

}
