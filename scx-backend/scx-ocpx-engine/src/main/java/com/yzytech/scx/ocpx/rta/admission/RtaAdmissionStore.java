package com.yzytech.scx.ocpx.rta.admission;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;



/** RTA 短期决定缓存与点击门禁存储。 */
@Component
public class RtaAdmissionStore {

	private static final String KEY_PREFIX = "rta:admission:";

	private static final String LOCK_PREFIX = "rta:lock:";

	private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
			"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
			Long.class);

	private final StringRedisTemplate redis;

	public RtaAdmissionStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public Admission find(String monitorTaskId, String requestId) {
		String value = redis.opsForValue().get(key(monitorTaskId, requestId));
		return value == null ? null : JSON.parseObject(value, Admission.class);
	}

	public void save(Admission admission, long ttlSeconds) {
		redis.opsForValue().set(key(admission.monitorTaskId, admission.requestId), JSON.toJSONString(admission),
				Math.max(1, ttlSeconds), TimeUnit.SECONDS);
	}

	/** Serialize decisions for a request ID so retries cannot overwrite a result. */
	public String acquire(String monitorTaskId, String requestId, long ttlMillis) {
		String token = UUID.randomUUID().toString();
		try {
			Boolean acquired = redis.opsForValue().setIfAbsent(lockKey(monitorTaskId, requestId), token,
					Math.max(1000, ttlMillis), TimeUnit.MILLISECONDS);
			return Boolean.TRUE.equals(acquired) ? token : null;
		}
		catch (RuntimeException exception) {
			throw new RtaInfrastructureException("RTA admission storage unavailable", exception);
		}
	}

	public void release(String monitorTaskId, String requestId, String token) {
		if (token == null) {
			return;
		}
		try {
			redis.execute(RELEASE_LOCK, List.of(lockKey(monitorTaskId, requestId)), token);
		}
		catch (RuntimeException ignored) {
			// The lock has a short TTL; a failed cleanup must not alter the decision result.
		}
	}

	public void requireAllowed(FeedbackReq request) {
		if (request == null || request.getEvent() == null || !Objects.equals(request.getEvent().name(), "CLICK")) {
			return;
		}
		Admission admission;
		try {
			admission = find(request.getTId(), request.getReqId());
		}
		catch (RuntimeException exception) {
			throw new RtaInfrastructureException("RTA admission storage unavailable", exception);
		}
		if (admission == null) {
			throw new RtaAdmissionDeniedException("RTA_ADMISSION_MISSING");
		}
		if (admission.expiresAtEpochSecond <= Instant.now().getEpochSecond()) {
			throw new RtaAdmissionDeniedException("RTA_ADMISSION_EXPIRED");
		}
		if (admission.decision != RtaDecision.ALLOW
				|| !Objects.equals(admission.requestId, request.getReqId())
				|| !Objects.equals(admission.monitorTaskId, request.getTId())
				|| !Objects.equals(admission.advertiserId, request.getAId())
				|| !Objects.equals(admission.promotionLinkId, request.getLId())
				|| !Objects.equals(admission.mediaId, request.getMId())) {
			throw new RtaAdmissionDeniedException("RTA_ADMISSION_MISMATCH");
		}
	}

	private String key(String monitorTaskId, String requestId) {
		return KEY_PREFIX + safe(monitorTaskId) + ":" + safe(requestId);
	}

	private String lockKey(String monitorTaskId, String requestId) {
		return LOCK_PREFIX + safe(monitorTaskId) + ":" + safe(requestId);
	}

	private String safe(String value) {
		return value == null ? "" : java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Admission {

		private String requestId;

		private String requestHash;

		private String advertiserId;

		private String promotionLinkId;

		private String mediaId;

		private String monitorTaskId;

		private String profileId;

		private RtaDecision decision;

		private String reasonCode;

		private List<String> taskIds;

		private long expiresAtEpochSecond;

	}

}
