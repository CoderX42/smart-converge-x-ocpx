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

import com.yzytech.scx.delivery.mapper.AdPlatformConfigMapper;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.dto.OutboxQueueStats;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.entity.ClickCallbackTarget;
import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import com.yzytech.scx.tracking.mapper.ClickCallbackTargetMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import com.yzytech.scx.tracking.mapper.ChannelClientMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CallbackJobServiceTest {

	private final CallbackJobMapper callbackMapper = mock(CallbackJobMapper.class);

	private final EventOutboxMapper outboxMapper = mock(EventOutboxMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final AdPlatformConfigMapper configMapper = mock(AdPlatformConfigMapper.class);

	private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);

	private final CallbackTransport transport = mock(CallbackTransport.class);

	private final TaskScopeService taskScopeService = mock(TaskScopeService.class);

	private final ClickCallbackTargetMapper callbackTargetMapper = mock(ClickCallbackTargetMapper.class);

	private final RawEventMapper rawEventMapper = mock(RawEventMapper.class);

	private final ChannelClientMapper channelClientMapper = mock(ChannelClientMapper.class);

	private final ChannelCredentialCrypto credentialCrypto = mock(ChannelCredentialCrypto.class);

	private final CallbackPayloadSnapshotService payloadSnapshotService = mock(CallbackPayloadSnapshotService.class);

	private final ChannelCredentialCache credentialCache = mock(ChannelCredentialCache.class);

	private final WorkLeaseService leaseService = mock(WorkLeaseService.class);

	private final CallbackJobService service = new CallbackJobService(callbackMapper, outboxMapper, scopeService,
			taskScopeService, taskMapper, configMapper, new TrackingProperties(), transport, Optional.empty(),
			callbackTargetMapper, rawEventMapper, channelClientMapper, credentialCrypto, payloadSnapshotService,
			credentialCache, leaseService);

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

		ArgumentCaptor<PostbackRequest> request = ArgumentCaptor.forClass(PostbackRequest.class);
		verify(transport).post(request.capture());
		assertThat(request.getValue().endpoint()).isEqualTo("http://localhost:8089/callback");
		assertThat(request.getValue().idempotencyKey()).isEqualTo("job-1");
		assertThat(request.getValue().payload()).contains("\"eventType\":\"activate\"");
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
		doThrow(new CallbackDeliveryException(true, "HTTP_503", "temporary failure")).when(transport).post(any());

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

		verify(transport, never()).post(any());
		assertThat(job.getStatus()).isEqualTo("DEAD");
		assertThat(job.getLastErrorCode()).isEqualTo("TARGET_NOT_ALLOWED");
	}

	@Test
	void rejectsDynamicCallbackResolvingToPrivateAddress() {
		CallbackJob job = job();
		job.setTargetRef("dynamic.click-1");
		allowAllTasks();
		when(callbackMapper.selectDispatchableByMedia("media-1", false, List.of())).thenReturn(List.of(job));
		when(callbackMapper.updateById(job)).thenReturn(1);
		ClickCallbackTarget target = new ClickCallbackTarget();
		target.setClickEventId("click-1");
		target.setEncryptedUrl("ciphertext");
		target.setUrlIv("iv");
		target.setUrlHost("127.0.0.1");
		target.setUrlFingerprint(com.yzytech.scx.common.core.sign.SignUtils.sha256("https://127.0.0.1/callback"));
		when(callbackTargetMapper.selectById("click-1")).thenReturn(target);
		RawEvent click = new RawEvent();
		click.setClientId("client-1");
		when(rawEventMapper.selectById("click-1")).thenReturn(click);
		ChannelClient client = new ChannelClient();
		client.setDynamicCallbackEnabled(true);
		client.setCallbackAllowedHosts("127.0.0.1");
		when(channelClientMapper.selectActiveByClientId("client-1")).thenReturn(client);
		when(credentialCrypto.decrypt("ciphertext", "iv")).thenReturn("https://127.0.0.1/callback");

		service.dispatchSandbox("media-1");

		verify(transport, never()).post(any());
		assertThat(job.getStatus()).isEqualTo("DEAD");
		assertThat(job.getLastErrorCode()).isEqualTo("TARGET_PRIVATE_ADDRESS");
	}

	@Test
	void publishesCommittedOutboxBeforeMarkingItSent() {
		TrackingProperties properties = new TrackingProperties();
		properties.setOutboxPublisherEnabled(true);
		OutboxPublisher publisher = mock(OutboxPublisher.class);
		TaskScopeService outboxTaskScopeService = mock(TaskScopeService.class);
		CallbackJobService outboxService = new CallbackJobService(callbackMapper, outboxMapper, scopeService,
				outboxTaskScopeService, mock(TrackingTaskMapper.class), configMapper, properties, transport,
				Optional.of(publisher), callbackTargetMapper, rawEventMapper, channelClientMapper, credentialCrypto,
				payloadSnapshotService, credentialCache, leaseService);
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
				outboxTaskScopeService, mock(TrackingTaskMapper.class), configMapper, properties, transport,
				Optional.of(publisher), callbackTargetMapper, rawEventMapper, channelClientMapper, credentialCrypto,
				payloadSnapshotService, credentialCache, leaseService);
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
				taskScopeService, mock(TrackingTaskMapper.class), configMapper, new TrackingProperties(), transport,
				Optional.empty(), callbackTargetMapper, rawEventMapper, channelClientMapper, credentialCrypto,
				payloadSnapshotService, credentialCache, leaseService);
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
				taskScopeService, taskMapper, configMapper, properties, transport,
				Optional.of(mock(OutboxPublisher.class)), callbackTargetMapper, rawEventMapper, channelClientMapper,
				credentialCrypto, payloadSnapshotService, credentialCache, leaseService);
		when(leaseService.claimCallbacks(any(Integer.class))).thenReturn(List.of());

		scheduledService.dispatchReadySandboxJobs();

		verify(leaseService).claimCallbacks(any(Integer.class));
		verify(leaseService, never()).claimOutbox(any(Integer.class));
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

		verify(transport).post(any(PostbackRequest.class));
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
		when(payloadSnapshotService.snapshot("job-1")).thenReturn(
				new CallbackPayloadSnapshotService.Snapshot("{\"eventType\":\"" + externalEvent + "\"}", "hash", null));
	}

}
