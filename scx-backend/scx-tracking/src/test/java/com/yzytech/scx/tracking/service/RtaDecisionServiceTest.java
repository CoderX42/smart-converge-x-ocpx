package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.entity.RtaEndpoint;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.tracking.dto.RtaDecisionRequest;
import com.yzytech.scx.tracking.dto.RtaDecisionResponse;
import com.yzytech.scx.tracking.dto.DeviceIdentifier;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RtaDecisionServiceTest {

	private final RtaConfigurationCache configurationCache = mock(RtaConfigurationCache.class);

	private final RtaDecisionLogBuffer logBuffer = mock(RtaDecisionLogBuffer.class);

	private final RtaTransport transport = mock(RtaTransport.class);

	private final DeviceIdentifierService deviceService = mock(DeviceIdentifierService.class);

	private final RtaDecisionService service = new RtaDecisionService(configurationCache, logBuffer, transport,
			new ObjectMapper(), deviceService);

	@Test
	void callsActiveTaskEndpointAndPersistsAllowDecision() {
		prepareDevice();
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
		when(configurationCache.resolve("TASK-1")).thenReturn(new RtaConfigurationCache.Configuration(task, endpoint));
		when(logBuffer.reserve("task-1", "rta-request-1"))
			.thenReturn(new RtaDecisionLogBuffer.Reservation(RtaDecisionLogBuffer.Status.NEW, null));
		when(transport.decide(any(), any())).thenReturn("{\"decision\":\"ALLOW\"}");

		RtaDecisionResponse response = service.decide(request(), "client-1");

		assertThat(response.decision()).isEqualTo("ALLOW");
		assertThat(response.duplicateDelivery()).isFalse();
		verify(logBuffer).complete(any(), any(), any(), any(RtaDecisionLog.class));
	}

	@Test
	void failsClosedWhenEndpointCannotBeReached() {
		prepareDevice();
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
		when(configurationCache.resolve("TASK-1")).thenReturn(new RtaConfigurationCache.Configuration(task, endpoint));
		when(logBuffer.reserve("task-1", "rta-request-1"))
			.thenReturn(new RtaDecisionLogBuffer.Reservation(RtaDecisionLogBuffer.Status.NEW, null));
		when(transport.decide(any(), any())).thenThrow(new RtaTransportException("NETWORK_ERROR", "down"));

		RtaDecisionResponse response = service.decide(request(), "client-1");

		assertThat(response.decision()).isEqualTo("DENY");
		assertThat(response.reasonCode()).isEqualTo("NETWORK_ERROR");
	}

	private RtaDecisionRequest request() {
		RtaDecisionRequest request = new RtaDecisionRequest();
		request.setSchemaVersion("1.1");
		request.setRequestId("rta-request-1");
		request.setTaskCode("TASK-1");
		DeviceIdentifier device = new DeviceIdentifier();
		device.setPlatform("IOS");
		device.setIdType("IDFA");
		device.setHashAlgorithm("SHA256");
		device.setIdHash("a".repeat(64));
		request.setDevice(device);
		request.setAttributes(Map.of("campaignId", "c-1"));
		return request;
	}

	private void prepareDevice() {
		when(deviceService.resolve(any(), any()))
			.thenReturn(new DeviceIdentifierService.ResolvedDevice("IOS", "IDFA", "", "v1", "b".repeat(64)));
	}

}
