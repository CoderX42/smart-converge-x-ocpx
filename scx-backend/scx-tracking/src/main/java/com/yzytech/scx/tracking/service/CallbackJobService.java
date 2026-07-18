package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.mapper.AdPlatformConfigMapper;
import com.yzytech.scx.delivery.mapper.LinkEventMappingMapper;
import com.yzytech.scx.delivery.entity.LinkEventMapping;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.dto.CallbackPayloadContext;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.tracking.config.TrackingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class CallbackJobService {

	private final CallbackJobMapper callbackMapper;

	private final EventOutboxMapper outboxMapper;

	private final MediaScopeService scopeService;

	private final TaskScopeService taskScopeService;

	private final TrackingTaskMapper taskMapper;

	private final AdPlatformConfigMapper platformConfigMapper;

	private final LinkEventMappingMapper mappingMapper;

	private final TrackingProperties trackingProperties;

	private final CallbackTransport callbackTransport;

	private final ObjectMapper objectMapper;

	private final Optional<OutboxPublisher> outboxPublisher;

	public IPage<CallbackJob> page(Page<CallbackJob> page, String mediaId, String status, String rawEventId) {
		TaskScopeService.TaskAccess access = taskScopeService.accessForMedia(mediaId, false);
		return callbackMapper.selectScopedPage(page, mediaId, status, rawEventId, access.restricted(),
				access.taskIds());
	}

	public CallbackQueueStats stats(String mediaId) {
		TaskScopeService.TaskAccess access = taskScopeService.accessForMedia(mediaId, false);
		CallbackQueueStats stats = callbackMapper.selectStats(mediaId, access.restricted(), access.taskIds());
		stats.setOutbox(outboxMapper.selectStats(mediaId, access.restricted(), access.taskIds()));
		return stats;
	}

	public IPage<EventOutbox> outboxPage(Page<EventOutbox> page, String mediaId, String status, String aggregateId) {
		TaskScopeService.TaskAccess access = taskScopeService.accessForMedia(mediaId, false);
		return outboxMapper.selectScopedPage(page, mediaId, status, aggregateId, access.restricted(), access.taskIds());
	}

	@Transactional
	public boolean replay(String id, String mediaId) {
		requireWrite(mediaId);
		if (!mediaId.equals(callbackMapper.selectMediaId(id)))
			throw new IllegalArgumentException("资源不存在");
		requireTaskWrite(id, mediaId);
		CallbackJob job = callbackMapper.selectById(id);
		if (job == null || !Set.of("DEAD", "RETRYING").contains(job.getStatus()))
			throw new IllegalArgumentException("回传任务不可重放");
		job.setStatus("PENDING");
		job.setNextRetryAt(null);
		job.setLastErrorCode(null);
		job.setLastErrorMessage(null);
		return callbackMapper.updateById(job) == 1;
	}

	@Transactional
	public boolean pause(String id, String mediaId) {
		requireWrite(mediaId);
		if (!mediaId.equals(callbackMapper.selectMediaId(id)))
			throw new IllegalArgumentException("资源不存在");
		requireTaskWrite(id, mediaId);
		CallbackJob job = callbackMapper.selectById(id);
		if (job == null || !Set.of("PENDING", "RETRYING").contains(job.getStatus()))
			throw new IllegalArgumentException("当前回传任务不可暂停");
		job.setStatus("PAUSED");
		return callbackMapper.updateById(job) == 1;
	}

	@Transactional
	public boolean resume(String id, String mediaId) {
		requireWrite(mediaId);
		if (!mediaId.equals(callbackMapper.selectMediaId(id)))
			throw new IllegalArgumentException("资源不存在");
		requireTaskWrite(id, mediaId);
		CallbackJob job = callbackMapper.selectById(id);
		if (job == null || !"PAUSED".equals(job.getStatus()))
			throw new IllegalArgumentException("当前回传任务不可恢复");
		job.setStatus("PENDING");
		job.setNextRetryAt(null);
		return callbackMapper.updateById(job) == 1;
	}

	@Transactional
	public int dispatchSandbox(String mediaId) {
		TaskScopeService.TaskAccess access = taskScopeService.accessForMedia(mediaId, true);
		return dispatchInternal(mediaId, access, true);
	}

	@Transactional
	public boolean dispatchSandboxJob(String id, String mediaId) {
		requireWrite(mediaId);
		if (!mediaId.equals(callbackMapper.selectMediaId(id)))
			throw new IllegalArgumentException("资源不存在");
		requireTaskWrite(id, mediaId);
		CallbackJob job = callbackMapper.selectById(id);
		if (job == null || !Set.of("PENDING", "RETRYING").contains(job.getStatus())) {
			throw new IllegalArgumentException("当前回传任务不可派发");
		}
		if (job.getNextRetryAt() != null && job.getNextRetryAt().isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("回传任务尚未到派发时间");
		}
		dispatchJob(job, mediaId);
		return callbackMapper.updateById(job) == 1;
	}

	@Transactional
	public boolean dispatchSandboxOutbox(String id, String mediaId) {
		TaskScopeService.TaskAccess access = taskScopeService.accessForMedia(mediaId, true);
		EventOutbox event = outboxMapper.selectScopedById(id, mediaId, access.restricted(), access.taskIds());
		if (event == null)
			throw new IllegalArgumentException("资源不存在");
		if (!"PENDING".equals(event.getStatus()) && !"RETRYING".equals(event.getStatus())) {
			throw new IllegalArgumentException("当前发件箱记录不可发布");
		}
		if (event.getNextRetryAt() != null && event.getNextRetryAt().isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("发件箱记录尚未到发布时机");
		}
		return dispatchOutbox(event) == 1;
	}

	@Scheduled(fixedDelayString = "${scx.tracking.callback-dispatch-delay-ms:30000}")
	public void dispatchReadySandboxJobs() {
		Set<String> mediaIds = new HashSet<>(callbackMapper.selectMediaIdsWithDispatchableJobs());
		boolean dispatchOutbox = trackingProperties.isOutboxPublisherEnabled()
				&& trackingProperties.isOutboxSchedulerEnabled();
		if (dispatchOutbox) {
			mediaIds.addAll(outboxMapper.selectMediaIdsWithDispatchableOutbox());
		}
		for (String mediaId : mediaIds) {
			dispatchInternal(mediaId, TaskScopeService.TaskAccess.unrestricted(), dispatchOutbox);
		}
	}

	@Transactional
	protected int dispatchInternal(String mediaId, TaskScopeService.TaskAccess taskAccess, boolean dispatchOutbox) {
		int changed = 0;
		if (dispatchOutbox && trackingProperties.isOutboxPublisherEnabled()) {
			for (EventOutbox value : outboxMapper.selectPendingByMedia(mediaId, taskAccess.restricted(),
					taskAccess.taskIds())) {
				changed += dispatchOutbox(value);
			}
		}
		for (CallbackJob job : callbackMapper.selectDispatchableByMedia(mediaId, taskAccess.restricted(),
				taskAccess.taskIds())) {
			dispatchJob(job, mediaId);
			changed += callbackMapper.updateById(job);
		}
		return changed;
	}

	private int dispatchOutbox(EventOutbox value) {
		value.setAttempts(value.getAttempts() + 1);
		try {
			OutboxPublisher publisher = outboxPublisher.orElseThrow(() -> new IllegalStateException("Outbox 发布器未启用"));
			publisher.publish(value);
			value.setStatus("SENT");
			value.setSentAt(LocalDateTime.now());
			value.setNextRetryAt(null);
		}
		catch (Exception exception) {
			boolean dead = value.getAttempts() >= 5;
			value.setStatus(dead ? "DEAD" : "RETRYING");
			value.setNextRetryAt(dead ? null : LocalDateTime.now().plusMinutes(1L << Math.min(value.getAttempts(), 6)));
		}
		return outboxMapper.updateById(value);
	}

	private void dispatchJob(CallbackJob job, String mediaId) {
		job.setAttempts(job.getAttempts() + 1);
		try {
			callbackTransport.post(resolveEndpoint(mediaId, job.getTargetRef()), job.getId(), payload(job));
			job.setStatus("SUCCEEDED");
			job.setNextRetryAt(null);
			job.setLastErrorCode(null);
			job.setLastErrorMessage(null);
		}
		catch (CallbackDeliveryException exception) {
			failDelivery(job, exception.retryable(), exception.code(), exception.getMessage());
		}
		catch (Exception exception) {
			failDelivery(job, true, "DISPATCH_ERROR", "回传调度失败");
		}
	}

	private String resolveEndpoint(String mediaId, String targetRef) {
		if (targetRef == null || !targetRef.matches("[A-Za-z0-9._-]{1,64}")) {
			throw new CallbackDeliveryException(false, "TARGET_INVALID", "回传目标标识无效");
		}
		String endpoint = platformConfigMapper.selectActiveValue(mediaId, "callback.endpoint." + targetRef);
		if (endpoint == null && "sandbox".equals(targetRef))
			endpoint = trackingProperties.getCallbackSandboxEndpoint();
		if (endpoint == null)
			throw new CallbackDeliveryException(false, "TARGET_MISSING", "未配置回传目标地址");
		try {
			URI uri = URI.create(endpoint);
			String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
			Set<String> allowedHosts = trackingProperties.getCallbackAllowedHosts()
				.stream()
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(java.util.stream.Collectors.toSet());
			if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null || host == null
					|| !allowedHosts.contains(host)) {
				throw new CallbackDeliveryException(false, "TARGET_NOT_ALLOWED", "回传目标不在允许域名列表");
			}
			return endpoint;
		}
		catch (CallbackDeliveryException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new CallbackDeliveryException(false, "TARGET_INVALID", "回传目标地址格式无效");
		}
	}

	private String payload(CallbackJob job) {
		try {
			CallbackPayloadContext context = callbackMapper.selectPayloadContext(job.getId());
			if (context == null || context.getInternalEvent() == null || context.getLinkId() == null
					|| context.getLinkConfigVersion() == null) {
				throw new CallbackDeliveryException(false, "PAYLOAD_CONTEXT_MISSING", "回传上下文不存在");
			}
			LinkEventMapping mapping = mappingMapper
				.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<LinkEventMapping>lambdaQuery()
					.eq(LinkEventMapping::getLinkId, context.getLinkId())
					.eq(LinkEventMapping::getConfigVersion, context.getLinkConfigVersion())
					.eq(LinkEventMapping::getInternalEvent, context.getInternalEvent())
					.last("LIMIT 1"));
			String externalEvent = mapping == null ? context.getInternalEvent() : mapping.getExternalEvent();
			return objectMapper.writeValueAsString(Map.of("callbackJobId", job.getId(), "attributionResultId",
					job.getAttributionResultId(), "actionType", job.getActionType(), "attempt", job.getAttempts(),
					"taskCode", context.getTaskCode(), "internalEvent", context.getInternalEvent(), "eventType",
					externalEvent, "occurredAt", context.getOccurredAt().toString()));
		}
		catch (CallbackDeliveryException exception) {
			throw exception;
		}
		catch (JsonProcessingException exception) {
			throw new CallbackDeliveryException(false, "PAYLOAD_ERROR", "回传载荷生成失败");
		}
	}

	private void failDelivery(CallbackJob job, boolean retryable, String code, String message) {
		boolean dead = !retryable || job.getAttempts() >= 5;
		job.setStatus(dead ? "DEAD" : "RETRYING");
		job.setLastErrorCode(code);
		job.setLastErrorMessage(message);
		job.setNextRetryAt(dead ? null : LocalDateTime.now().plusMinutes(1L << Math.min(job.getAttempts(), 6)));
	}

	private void requireRead(String mediaId) {
		if (mediaId == null || !scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void requireWrite(String mediaId) {
		if (mediaId == null || !scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void requireTaskWrite(String callbackJobId, String mediaId) {
		String taskId = callbackMapper.selectTaskId(callbackJobId, mediaId);
		TrackingTask task = taskId == null ? null : taskMapper.selectById(taskId);
		if (task == null || !taskScopeService.canWrite(task))
			throw new IllegalArgumentException("资源不存在");
	}

}
