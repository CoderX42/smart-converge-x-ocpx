package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.mapper.AdPlatformConfigMapper;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import com.yzytech.scx.tracking.mapper.ClickCallbackTargetMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import com.yzytech.scx.tracking.mapper.ChannelClientMapper;
import com.yzytech.scx.tracking.entity.ClickCallbackTarget;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.entity.ChannelClient;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.tracking.config.TrackingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CallbackJobService {

	private final CallbackJobMapper callbackMapper;

	private final EventOutboxMapper outboxMapper;

	private final MediaScopeService scopeService;

	private final TaskScopeService taskScopeService;

	private final TrackingTaskMapper taskMapper;

	private final AdPlatformConfigMapper platformConfigMapper;

	private final TrackingProperties trackingProperties;

	private final CallbackTransport callbackTransport;

	private final Optional<OutboxPublisher> outboxPublisher;

	private final ClickCallbackTargetMapper callbackTargetMapper;

	private final RawEventMapper rawEventMapper;

	private final ChannelClientMapper channelClientMapper;

	private final ChannelCredentialCrypto credentialCrypto;

	private final CallbackPayloadSnapshotService payloadSnapshotService;

	private final ChannelCredentialCache credentialCache;

	private final WorkLeaseService leaseService;

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

	public int dispatchSandbox(String mediaId) {
		TaskScopeService.TaskAccess access = taskScopeService.accessForMedia(mediaId, true);
		return dispatchInternal(mediaId, access, true);
	}

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

	public boolean dispatchSandboxOutbox(String id, String mediaId) {
		TaskScopeService.TaskAccess access = taskScopeService.accessForMedia(mediaId, true);
		EventOutbox event = outboxMapper.selectScopedById(id, mediaId, access.restricted(), access.taskIds());
		if (event == null)
			throw new IllegalArgumentException("资源不存在");
		if (!"PENDING".equals(event.getStatus()) && !"RETRYING".equals(event.getStatus())) {
			throw new IllegalArgumentException("当前发件箱记录不可发布");
		}
		// An explicitly authorized single-record dispatch is a recovery operation.
		// It may bypass the scheduler backoff; the scheduler itself still honours
		// nextRetryAt through selectPendingByMedia(...).
		return dispatchOutbox(event) == 1;
	}

	@Scheduled(fixedDelayString = "${scx.tracking.callback-dispatch-delay-ms:30000}")
	public void dispatchReadySandboxJobs() {
		if (trackingProperties.isOutboxPublisherEnabled() && trackingProperties.isOutboxSchedulerEnabled()) {
			for (EventOutbox value : leaseService.claimOutbox(trackingProperties.getWorkerBatchSize())) {
				deliverOutbox(value);
				leaseService.completeOutbox(value);
			}
		}
		for (CallbackJob job : leaseService.claimCallbacks(trackingProperties.getWorkerBatchSize())) {
			String mediaId = callbackMapper.selectMediaId(job.getId());
			if (mediaId == null)
				failDelivery(job, false, "MEDIA_CONTEXT_MISSING", "回传任务媒体上下文不存在");
			else
				dispatchJob(job, mediaId);
			leaseService.completeCallback(job);
		}
	}

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
		deliverOutbox(value);
		return outboxMapper.updateById(value);
	}

	private void deliverOutbox(EventOutbox value) {
		value.setAttempts(value.getAttempts() + 1);
		try {
			OutboxPublisher publisher = outboxPublisher.orElseThrow(() -> new IllegalStateException("Outbox 发布器未启用"));
			publisher.publish(value);
			value.setStatus("SENT");
			value.setSentAt(LocalDateTime.now());
			value.setNextRetryAt(null);
			value.setLastErrorCode(null);
			value.setLastErrorMessage(null);
		}
		catch (Exception exception) {
			boolean dead = value.getAttempts() >= 5;
			value.setStatus(dead ? "DEAD" : "RETRYING");
			long[] delays = { 1, 2, 5, 10 };
			value.setNextRetryAt(dead ? null : LocalDateTime.now()
				.plusMinutes(delays[Math.max(0, Math.min(value.getAttempts() - 1, delays.length - 1))]));
			value.setLastErrorCode("OUTBOX_PUBLISH_FAILED");
			value.setLastErrorMessage("发件箱发布失败");
		}
	}

	private void dispatchJob(CallbackJob job, String mediaId) {
		job.setAttempts(job.getAttempts() + 1);
		try {
			String endpoint = resolveEndpoint(mediaId, job.getTargetRef());
			CallbackPayloadSnapshotService.Snapshot snapshot = payloadSnapshotService.snapshot(job.getId());
			ChannelCredentialCache.CachedCredential credential = snapshot.clientId() == null ? null
					: credentialCache.resolveCurrent(snapshot.clientId());
			if (snapshot.clientId() != null && credential == null)
				throw new CallbackDeliveryException(false, "CHANNEL_CREDENTIAL_UNAVAILABLE", "渠道回传密钥不可用");
			callbackTransport.post(new PostbackRequest(endpoint, job.getId(), snapshot.payload(), snapshot.clientId(),
					credential == null ? null : credential.keyVersion(),
					credential == null ? null : credential.secret()));
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
		if (targetRef != null && targetRef.startsWith("dynamic."))
			return resolveDynamicEndpoint(targetRef.substring("dynamic.".length()));
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

	private String resolveDynamicEndpoint(String clickEventId) {
		ClickCallbackTarget target = callbackTargetMapper.selectById(clickEventId);
		RawEvent click = rawEventMapper.selectById(clickEventId);
		if (target == null || click == null || !StringUtils.hasText(target.getEncryptedUrl()))
			throw new CallbackDeliveryException(false, "TARGET_MISSING", "动态回传目标不存在");
		ChannelClient client = channelClientMapper.selectActiveByClientId(click.getClientId());
		if (client == null || !Boolean.TRUE.equals(client.getDynamicCallbackEnabled()))
			throw new CallbackDeliveryException(false, "TARGET_DISABLED", "动态回传目标已停用");
		String endpoint;
		try {
			endpoint = credentialCrypto.decrypt(target.getEncryptedUrl(), target.getUrlIv());
		}
		catch (IllegalStateException exception) {
			throw new CallbackDeliveryException(false, "TARGET_DECRYPT_FAILED", "动态回传目标无法解密");
		}
		try {
			URI uri = URI.create(endpoint);
			String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
			Set<String> allowedHosts = java.util.Arrays
				.stream(client.getCallbackAllowedHosts() == null ? new String[0]
						: client.getCallbackAllowedHosts().split(","))
				.map(String::trim)
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(java.util.stream.Collectors.toSet());
			if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || host == null
					|| !host.equals(target.getUrlHost()) || !allowedHosts.contains(host)
					|| !com.yzytech.scx.common.core.sign.SignUtils.sha256(endpoint).equals(target.getUrlFingerprint()))
				throw new CallbackDeliveryException(false, "TARGET_NOT_ALLOWED", "动态回传目标不在允许范围");
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
						|| address.isSiteLocalAddress() || address.isMulticastAddress())
					throw new CallbackDeliveryException(false, "TARGET_PRIVATE_ADDRESS", "动态回传目标解析到非公网地址");
			}
			return endpoint;
		}
		catch (CallbackDeliveryException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new CallbackDeliveryException(true, "TARGET_DNS_ERROR", "动态回传目标解析失败");
		}
	}

	private void failDelivery(CallbackJob job, boolean retryable, String code, String message) {
		boolean dead = !retryable || job.getAttempts() >= 5;
		job.setStatus(dead ? "DEAD" : "RETRYING");
		job.setLastErrorCode(code);
		job.setLastErrorMessage(message);
		long[] delays = { 1, 2, 5, 10 };
		job.setNextRetryAt(dead ? null : LocalDateTime.now()
			.plusMinutes(delays[Math.max(0, Math.min(job.getAttempts() - 1, delays.length - 1))]));
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
