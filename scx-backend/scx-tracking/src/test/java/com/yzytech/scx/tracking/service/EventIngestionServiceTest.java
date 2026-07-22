package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.tracking.dto.DeviceIdentifier;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import com.yzytech.scx.tracking.dto.EventIngestionResponse;
import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventIngestionServiceTest {

	private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);

	private final ChannelRequestAuthenticator authenticator = mock(ChannelRequestAuthenticator.class);

	private final DeviceIdentifierService deviceService = mock(DeviceIdentifierService.class);

	private final FeedbackBuffer feedbackBuffer = mock(FeedbackBuffer.class);

	private final AcceptedEventPersistenceService persistenceService = mock(AcceptedEventPersistenceService.class);

	private EventIngestionService service;

	@BeforeEach
	void setUp() {
		service = new EventIngestionService(new ObjectMapper().findAndRegisterModules(), new TrackingProperties(),
				taskMapper, mock(RawEventMapper.class), authenticator, deviceService,
				mock(ChannelCredentialCrypto.class), feedbackBuffer, persistenceService);
	}

	@Test
	void canonicalPayloadSortsAttributesAndKeepsTimezone() {
		EventIngestionRequest first = request("CLICK");
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("z", 1);
		attributes.put("a", 2);
		first.setAttributes(attributes);
		EventIngestionRequest second = request("CLICK");
		second.setAttributes(Map.of("a", 2, "z", 1));

		assertThat(service.canonicalPayload(first)).isEqualTo(service.canonicalPayload(second));
		assertThat(service.canonicalPayload(first)).contains("\"occurredAt\":\"2026-07-16T12:00+08:00\"");
	}

	@Test
	void rejectsOldSchemaBeforeAuthentication() {
		EventIngestionRequest request = request("CLICK");
		request.setSchemaVersion("1.0");

		assertThatThrownBy(() -> service.accept("client-1", "v1", "1", "nonce", "sign", "/open-api/events/v1/clicks",
				"CLICK", request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("仅支持 schemaVersion=1.1");
	}

	@Test
	void clickIsAcknowledgedOnlyAfterFeedbackBufferAcceptsIt() {
		prepareDependencies();
		when(feedbackBuffer.enqueue(any())).thenAnswer(invocation -> {
			AcceptedEventCommand command = invocation.getArgument(0);
			return new FeedbackBuffer.EnqueueResult(FeedbackBuffer.Status.ACCEPTED, command.receiptId());
		});

		EventIngestionResponse response = service.accept("client-1", "v1", "1", "nonce-1234567890123456", "sign",
				"/open-api/events/v1/clicks", "CLICK", request("CLICK"));

		assertThat(response.acceptanceStatus()).isEqualTo("BUFFERED");
		assertThat(response.duplicateDelivery()).isFalse();
		verify(feedbackBuffer).enqueue(any(AcceptedEventCommand.class));
	}

	@Test
	void conversionPersistsRawFactBeforeReturningAccepted() {
		prepareDependencies();
		OffsetDateTime receivedAt = OffsetDateTime.of(2026, 7, 16, 4, 0, 1, 0, ZoneOffset.UTC);
		when(persistenceService.persist(any()))
			.thenReturn(new AcceptedEventPersistenceService.PersistenceResult("raw-1", "receipt-1", false, receivedAt));
		EventIngestionRequest request = request("PAY");
		request.setBusinessEventKey("order-1");

		EventIngestionResponse response = service.accept("client-1", "v1", "1", "nonce-1234567890123456", "sign",
				"/open-api/events/v1/conversions", null, request);

		assertThat(response.receiptId()).isEqualTo("receipt-1");
		assertThat(response.acceptanceStatus()).isEqualTo("ACCEPTED");
		verify(persistenceService).persist(any(AcceptedEventCommand.class));
	}

	private void prepareDependencies() {
		ChannelClient client = new ChannelClient();
		client.setClientId("client-1");
		client.setSourceCode("channel-a");
		when(authenticator.authenticate(eq("client-1"), eq("v1"), any(), any(), any(), eq("POST"), any(), any()))
			.thenReturn(new ChannelAuthContext(client, "v1", "secret"));
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setCode("TASK-1");
		task.setStatus("ACTIVE");
		task.setDedupWindowSec(300);
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(deviceService.resolve(any(), eq("client-1")))
			.thenReturn(new DeviceIdentifierService.ResolvedDevice("ANDROID", "OAID", "", "v1", "b".repeat(64)));
	}

	private EventIngestionRequest request(String eventType) {
		EventIngestionRequest value = new EventIngestionRequest();
		value.setSchemaVersion("1.1");
		value.setEventId("evt-1");
		value.setTaskCode("TASK-1");
		value.setEventType(eventType);
		value.setOccurredAt(OffsetDateTime.parse("2026-07-16T12:00:00+08:00"));
		DeviceIdentifier device = new DeviceIdentifier();
		device.setPlatform("ANDROID");
		device.setIdType("OAID");
		device.setHashAlgorithm("SHA256");
		device.setIdHash("a".repeat(64));
		value.setDevice(device);
		return value;
	}

}
