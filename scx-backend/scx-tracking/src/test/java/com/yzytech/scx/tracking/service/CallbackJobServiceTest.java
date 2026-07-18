package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.mapper.AdPlatformConfigMapper;
import com.yzytech.scx.delivery.mapper.LinkEventMappingMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.dto.CallbackPayloadContext;
import com.yzytech.scx.tracking.dto.OutboxQueueStats;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CallbackJobServiceTest {

	private final CallbackJobMapper callbackMapper = mock(CallbackJobMapper.class);

	private final EventOutboxMapper outboxMapper = mock(EventOutboxMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final AdPlatformConfigMapper configMapper = mock(AdPlatformConfigMapper.class);

	private final LinkEventMappingMapper mappingMapper = mock(LinkEventMappingMapper.class);

	private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);

	private final CallbackTransport transport = mock(CallbackTransport.class);

	private final TaskScopeService taskScopeService = mock(TaskScopeService.class);

	private final CallbackJobService service = new CallbackJobService(callbackMapper, outboxMapper, scopeService,
			taskScopeService, taskMapper, configMapper, mappingMapper, new TrackingProperties(), transport,
			new ObjectMapper(), Optional.empty());

	@Test
	void postsConfiguredCallbackWithIdempotencyKey() {
		CallbackJob job = job();
		allowAllTasks();
		when(callbackMapper.selectDispatchableByMedia("media-1", false, List.of())).thenReturn(List.of(job));
		when(callbackMapper.updateById(job)).thenReturn(1);
		when(configMapper.selectActiveValue("media-1", "callback.endpoint.partner-a"))
			.thenReturn("http://localhost:8089/callback");
		stubCallbackContext("activate");

		assertThat(service.dispatchSandbox("media-1")).isEqualTo(1);

		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(transport).post(eq("http://localhost:8089/callback"), eq("job-1"), payload.capture());
		assertThat(payload.getValue()).contains("\"eventType\":\"activate\"");
		assertThat(job.getStatus()).isEqualTo("SUCCEEDED");
		assertThat(job.getAttempts()).isEqualTo(1);
		verify(callbackMapper).updateById(job);
	}

	@Test
	void retriesRetryableTransportFailures() {
		CallbackJob job = job();
		allowAllTasks();
		when(callbackMapper.selectDispatchableByMedia("media-1", false, List.of())).thenReturn(List.of(job));
		when(callbackMapper.updateById(job)).thenReturn(1);
		when(configMapper.selectActiveValue("media-1", "callback.endpoint.partner-a"))
			.thenReturn("http://localhost:8089/callback");
		stubCallbackContext("activate");
		doThrow(new CallbackDeliveryException(true, "HTTP_503", "temporary failure")).when(transport)
			.post(any(), any(), any());

		service.dispatchSandbox("media-1");

		assertThat(job.getStatus()).isEqualTo("RETRYING");
		assertThat(job.getLastErrorCode()).isEqualTo("HTTP_503");
		assertThat(job.getNextRetryAt()).isNotNull();
	}

	@Test
	void blocksCallbackHostsOutsideTheConfiguredAllowlist() {
		CallbackJob job = job();
		allowAllTasks();
		when(callbackMapper.selectDispatchableByMedia("media-1", false, List.of())).thenReturn(List.of(job));
		when(callbackMapper.updateById(job)).thenReturn(1);
		when(configMapper.selectActiveValue("media-1", "callback.endpoint.partner-a"))
			.thenReturn("https://untrusted.example/callback");

		service.dispatchSandbox("media-1");

		verify(transport, never()).post(any(), any(), any());
		assertThat(job.getStatus()).isEqualTo("DEAD");
		assertThat(job.getLastErrorCode()).isEqualTo("TARGET_NOT_ALLOWED");
	}

	@Test
	void publishesCommittedOutboxBeforeMarkingItSent() {
		TrackingProperties properties = new TrackingProperties();
		properties.setOutboxPublisherEnabled(true);
		OutboxPublisher publisher = mock(OutboxPublisher.class);
		TaskScopeService outboxTaskScopeService = mock(TaskScopeService.class);
		CallbackJobService outboxService = new CallbackJobService(callbackMapper, outboxMapper, scopeService,
				outboxTaskScopeService, mock(TrackingTaskMapper.class), configMapper, mappingMapper, properties,
				transport, new ObjectMapper(), Optional.of(publisher));
		EventOutbox event = new EventOutbox();
		event.setId("outbox-1");
		event.setAggregateId("event-1");
		event.setEventType("RAW_EVENT_INGESTED");
		event.setPayloadJson("{}");
		event.setAttempts(0);
		event.setStatus("PENDING");
		when(outboxTaskScopeService.accessForMedia("media-1", true))
			.thenReturn(TaskScopeService.TaskAccess.unrestricted());
		when(outboxMapper.selectPendingByMedia("media-1", false, List.of())).thenReturn(List.of(event));
		when(callbackMapper.selectDispatchableByMedia("media-1", false, List.of())).thenReturn(List.of());
		when(outboxMapper.updateById(event)).thenReturn(1);

		assertThat(outboxService.dispatchSandbox("media-1")).isEqualTo(1);

		verify(publisher).publish(event);
		assertThat(event.getStatus()).isEqualTo("SENT");
		assertThat(event.getAttempts()).isEqualTo(1);
	}

	@Test
	void publishesOnlyTheAuthorizedTargetedOutboxEvent() {
		TrackingProperties properties = new TrackingProperties();
		properties.setOutboxPublisherEnabled(true);
		OutboxPublisher publisher = mock(OutboxPublisher.class);
		TaskScopeService outboxTaskScopeService = mock(TaskScopeService.class);
		CallbackJobService outboxService = new CallbackJobService(callbackMapper, outboxMapper, scopeService,
				outboxTaskScopeService, mock(TrackingTaskMapper.class), configMapper, mappingMapper, properties,
				transport, new ObjectMapper(), Optional.of(publisher));
		EventOutbox event = new EventOutbox();
		event.setId("outbox-1");
		event.setAggregateId("event-1");
		event.setEventType("RAW_EVENT_INGESTED");
		event.setPayloadJson("{}");
		event.setAttempts(0);
		event.setStatus("PENDING");
		when(outboxTaskScopeService.accessForMedia("media-1", true))
			.thenReturn(TaskScopeService.TaskAccess.unrestricted());
		when(outboxMapper.selectScopedById("outbox-1", "media-1", false, List.of())).thenReturn(event);
		when(outboxMapper.updateById(event)).thenReturn(1);

		assertThat(outboxService.dispatchSandboxOutbox("outbox-1", "media-1")).isTrue();

		verify(publisher).publish(event);
		verify(outboxMapper, never()).selectPendingByMedia(any(), anyBoolean(), any());
		assertThat(event.getStatus()).isEqualTo("SENT");
		assertThat(event.getAttempts()).isEqualTo(1);
	}

	@Test
	void returnsCallbackAndOutboxRuntimeCountersInTheSameMediaScope() {
		TaskScopeService taskScopeService = mock(TaskScopeService.class);
		CallbackJobService queueService = new CallbackJobService(callbackMapper, outboxMapper, scopeService,
				taskScopeService, mock(TrackingTaskMapper.class), configMapper, mappingMapper, new TrackingProperties(),
				transport, new ObjectMapper(), Optional.empty());
		CallbackQueueStats callbackStats = new CallbackQueueStats();
		callbackStats.setPendingCount(3L);
		OutboxQueueStats outboxStats = new OutboxQueueStats();
		outboxStats.setRetryingCount(2L);
		when(taskScopeService.accessForMedia("media-1", false)).thenReturn(TaskScopeService.TaskAccess.unrestricted());
		when(callbackMapper.selectStats("media-1", false, List.of())).thenReturn(callbackStats);
		when(outboxMapper.selectStats("media-1", false, List.of())).thenReturn(outboxStats);

		CallbackQueueStats result = queueService.stats("media-1");

		assertThat(result.getPendingCount()).isEqualTo(3L);
		assertThat(result.getOutbox().getRetryingCount()).isEqualTo(2L);
	}

	@Test
	void manualDispatchOnlyQueriesTasksInTheCallerScope() {
		when(taskScopeService.accessForMedia("media-1", true))
			.thenReturn(TaskScopeService.TaskAccess.restricted(List.of("task-allowed")));
		when(callbackMapper.selectDispatchableByMedia("media-1", true, List.of("task-allowed"))).thenReturn(List.of());

		assertThat(service.dispatchSandbox("media-1")).isZero();

		verify(callbackMapper).selectDispatchableByMedia("media-1", true, List.of("task-allowed"));
	}

	@Test
	void keepsCallbackSchedulingButPausesOnlyTheScheduledOutboxScan() {
		TrackingProperties properties = new TrackingProperties();
		properties.setOutboxPublisherEnabled(true);
		properties.setOutboxSchedulerEnabled(false);
		CallbackJobService scheduledService = new CallbackJobService(callbackMapper, outboxMapper, scopeService,
				taskScopeService, taskMapper, configMapper, mappingMapper, properties, transport, new ObjectMapper(),
				Optional.of(mock(OutboxPublisher.class)));
		when(callbackMapper.selectMediaIdsWithDispatchableJobs()).thenReturn(List.of("media-1"));
		when(callbackMapper.selectDispatchableByMedia("media-1", false, List.of())).thenReturn(List.of());

		scheduledService.dispatchReadySandboxJobs();

		verify(callbackMapper).selectDispatchableByMedia("media-1", false, List.of());
		verify(outboxMapper, never()).selectMediaIdsWithDispatchableOutbox();
		verify(outboxMapper, never()).selectPendingByMedia(any(), anyBoolean(), any());
	}

	@Test
	void dispatchesOnlyTheAuthorizedTargetedCallbackJob() {
		CallbackJob job = job();
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(callbackMapper.selectMediaId("job-1")).thenReturn("media-1");
		when(callbackMapper.selectTaskId("job-1", "media-1")).thenReturn("task-1");
		when(taskMapper.selectById("task-1")).thenReturn(task);
		when(taskScopeService.canWrite(task)).thenReturn(true);
		when(callbackMapper.selectById("job-1")).thenReturn(job);
		when(callbackMapper.updateById(job)).thenReturn(1);
		when(configMapper.selectActiveValue("media-1", "callback.endpoint.partner-a"))
			.thenReturn("http://localhost:8089/callback");
		stubCallbackContext("activate");

		assertThat(service.dispatchSandboxJob("job-1", "media-1")).isTrue();

		verify(transport).post(eq("http://localhost:8089/callback"), eq("job-1"), any());
		verify(callbackMapper, never()).selectDispatchableByMedia(any(), anyBoolean(), any());
		verify(outboxMapper, never()).selectPendingByMedia(any(), anyBoolean(), any());
	}

	private CallbackJob job() {
		CallbackJob job = new CallbackJob();
		job.setId("job-1");
		job.setAttributionResultId("attribution-1");
		job.setActionType("ORIGINAL");
		job.setTargetRef("partner-a");
		job.setStatus("PENDING");
		job.setAttempts(0);
		return job;
	}

	private void allowAllTasks() {
		when(taskScopeService.accessForMedia("media-1", true)).thenReturn(TaskScopeService.TaskAccess.unrestricted());
	}

	private void stubCallbackContext(String externalEvent) {
		CallbackPayloadContext context = new CallbackPayloadContext();
		context.setTaskCode("TASK-1");
		context.setLinkId("link-1");
		context.setLinkConfigVersion(1);
		context.setInternalEvent("ACTIVATE");
		context.setOccurredAt(java.time.LocalDateTime.of(2026, 7, 16, 12, 0));
		com.yzytech.scx.delivery.entity.LinkEventMapping mapping = new com.yzytech.scx.delivery.entity.LinkEventMapping();
		mapping.setExternalEvent(externalEvent);
		when(callbackMapper.selectPayloadContext("job-1")).thenReturn(context);
		when(mappingMapper.selectOne(any())).thenReturn(mapping);
	}

}
