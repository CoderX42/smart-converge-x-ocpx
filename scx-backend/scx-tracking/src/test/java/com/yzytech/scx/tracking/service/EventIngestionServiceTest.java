package com.yzytech.scx.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.entity.LinkEventMapping;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.mapper.LinkEventMappingMapper;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import com.yzytech.scx.tracking.dto.EventIngestionResponse;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.AttributionResultMapper;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.ClickDedupResultMapper;
import com.yzytech.scx.tracking.mapper.ConversionDedupResultMapper;
import com.yzytech.scx.tracking.mapper.ConversionIdentityMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class EventIngestionServiceTest {

	private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);

	private final RawEventMapper rawEventMapper = mock(RawEventMapper.class);

	private final LinkEventMappingMapper mappingMapper = mock(LinkEventMappingMapper.class);

	private final AttributionResultMapper attributionMapper = mock(AttributionResultMapper.class);

	private EventIngestionService service;

	@BeforeEach
	void setUp() {
		service = new EventIngestionService(new TrackingProperties(), new ObjectMapper().findAndRegisterModules(),
				taskMapper, mappingMapper, rawEventMapper, mock(ClickDedupResultMapper.class),
				mock(ConversionIdentityMapper.class), mock(ConversionDedupResultMapper.class), attributionMapper,
				mock(EventOutboxMapper.class), mock(CallbackJobMapper.class));
	}

	@Test
	void canonicalPayloadSortsAttributeKeys() {
		EventIngestionRequest first = request();
		Map<String, Object> firstAttributes = new LinkedHashMap<>();
		firstAttributes.put("z", 1);
		firstAttributes.put("a", 2);
		first.setAttributes(firstAttributes);
		EventIngestionRequest second = request();
		second.setAttributes(Map.of("a", 2, "z", 1));

		assertThat(service.canonicalPayload(first)).isEqualTo(service.canonicalPayload(second));
		assertThat(service.canonicalPayload(first)).contains("\"occurredAt\":\"2026-07-16T12:00:00\"");
	}

	@Test
	void canonicalPayloadNormalizesTrailingFractionalSecondZeros() {
		EventIngestionRequest request = request();
		request.setOccurredAt(LocalDateTime.of(2026, 7, 16, 12, 0, 0, 10_000_000));

		assertThat(service.canonicalPayload(request)).contains("\"occurredAt\":\"2026-07-16T12:00:00.01\"");
	}

	@Test
	void duplicateEventIdWithDifferentPayloadIsRejected() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setStatus("ACTIVE");
		when(taskMapper.selectOne(any())).thenReturn(task);
		RawEvent existing = new RawEvent();
		existing.setId("raw-1");
		existing.setPayloadHash("different-payload-hash");
		when(rawEventMapper.selectOne(any())).thenReturn(existing);

		assertThatThrownBy(() -> service.ingest(request())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("event_id 已被不同载荷占用");
	}

	@Test
	void impressionIsRecordedWithoutEnteringConversionAttribution() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setStatus("ACTIVE");
		task.setImpressionEnabled(true);
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(rawEventMapper.selectOne(any())).thenReturn(null);
		doAnswer(invocation -> {
			RawEvent stored = invocation.getArgument(0);
			stored.setId("impression-1");
			return 1;
		}).when(rawEventMapper).insert(any(RawEvent.class));
		EventIngestionRequest request = request();
		request.setEventType("IMPRESSION");

		EventIngestionResponse response = service.ingest(request);

		assertThat(response.rawEventId()).isEqualTo("impression-1");
		assertThat(response.reasonCode()).isEqualTo("IMPRESSION_RECORDED");
	}

	@Test
	void rejectsImpressionWhenTaskDidNotEnableExposureCollection() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setStatus("ACTIVE");
		task.setImpressionEnabled(false);
		when(taskMapper.selectOne(any())).thenReturn(task);
		EventIngestionRequest request = request();
		request.setEventType("IMPRESSION");

		assertThatThrownBy(() -> service.ingest(request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("监测任务未开启曝光上报");
	}

	@Test
	void acceptsOrderAsAttributedBusinessConversion() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setStatus("ACTIVE");
		task.setAttributionWindowHours(168);
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(rawEventMapper.selectOne(any())).thenReturn(null);
		doAnswer(invocation -> {
			RawEvent stored = invocation.getArgument(0);
			stored.setId("order-1");
			return 1;
		}).when(rawEventMapper).insert(any(RawEvent.class));
		EventIngestionRequest request = request();
		request.setEventType("ORDER");
		request.setBusinessEventKey("order-identity-1");

		EventIngestionResponse response = service.ingest(request);

		assertThat(response.reasonCode()).isEqualTo("FIRST_BUSINESS_EVENT");
	}

	@Test
	void marksConversionOverLimitWhenItsEventMappingQuotaIsReached() {
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setLinkId("link-1");
		task.setStatus("ACTIVE");
		task.setAttributionWindowHours(168);
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(rawEventMapper.selectOne(any())).thenReturn(null);
		doAnswer(invocation -> {
			RawEvent stored = invocation.getArgument(0);
			stored.setId("pay-1");
			return 1;
		}).when(rawEventMapper).insert(any(RawEvent.class));
		RawEvent click = new RawEvent();
		click.setId("click-1");
		when(rawEventMapper.findLastEligibleClick(any(), any(), any(), any())).thenReturn(click);
		LinkEventMapping mapping = new LinkEventMapping();
		mapping.setDailyLimit(1);
		when(mappingMapper.selectOne(any())).thenReturn(mapping);
		when(attributionMapper.countSettlementEligibleForTaskEventDate(any(), any(), any())).thenReturn(1L);
		EventIngestionRequest request = request();
		request.setEventType("PAY");
		request.setBusinessEventKey("pay-identity-1");

		service.ingest(request);

		ArgumentCaptor<com.yzytech.scx.tracking.entity.AttributionResult> captured = ArgumentCaptor
			.forClass(com.yzytech.scx.tracking.entity.AttributionResult.class);
		verify(attributionMapper).insert(captured.capture());
		assertThat(captured.getValue().getOverLimit()).isTrue();
	}

	private EventIngestionRequest request() {
		EventIngestionRequest value = new EventIngestionRequest();
		value.setSchemaVersion("1.0");
		value.setEventId("evt-1");
		value.setSource("sandbox-test");
		value.setTaskCode("TASK-1");
		value.setEventType("CLICK");
		value.setOccurredAt(LocalDateTime.of(2026, 7, 16, 12, 0));
		value.setDeviceKeyHash("a".repeat(64));
		return value;
	}

}
