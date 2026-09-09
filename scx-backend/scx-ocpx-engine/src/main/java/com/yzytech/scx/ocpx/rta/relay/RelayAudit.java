package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import com.yzytech.scx.ocpx.rta.contract.RtaState;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component
public class RelayAudit {

	private final StringRedisTemplate redis;

	private final MeterRegistry metrics;

	public RelayAudit(StringRedisTemplate redis, MeterRegistry metrics) {
		this.redis = redis;
		this.metrics = metrics;
	}

	public void write(RtaState s, String stage, boolean success, String reason, long duration) {
		metrics.counter("rta_stage_total", "stage", stage, "outcome", success ? "SUCCESS" : "FAILED").increment();
		if (success && Math.floorMod(s.getRelayRequestId().hashCode(), 10) != 0)
			return;
		for (var binding : s.getBindings()) {
			var value = new RtaContract.Log(UUID.randomUUID().toString().replace("-", ""), s.getRelayRequestId(),
					s.getOriginalRequestId() == null ? s.getRequestId() : s.getOriginalRequestId(), s.getMediaId(),
					s.getAdvertiserId(), s.getUpstream().id(), binding.taskId(), stage, success ? "SUCCESS" : "FAILED",
					reason, duration, System.currentTimeMillis());
			try {
				redis.opsForStream().add(RtaContract.PREFIX + "logs", Map.of("payload", RelayJson.write(value)));
			}
			catch (RuntimeException e) {
				metrics.counter("rta_log_enqueue_failures_total").increment();
			}
		}
	}

}
