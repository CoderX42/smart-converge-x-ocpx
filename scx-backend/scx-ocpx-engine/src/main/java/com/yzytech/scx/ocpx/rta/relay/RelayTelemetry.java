package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.function.Supplier;

/** Operational gauges are read at scrape time, independently of XXL-JOB. */
@Component
public class RelayTelemetry {

	public RelayTelemetry(StringRedisTemplate redis, MeterRegistry metrics) {
		for (String queue : List.of("send", "deadline", "notify"))
			metrics.gauge("rta_queue_depth", Tags.of("queue", queue), redis,
					r -> read(() -> r.opsForZSet().zCard(RtaContract.PREFIX + queue)));
		metrics.gauge("rta_log_backlog", redis, r -> read(() -> r.opsForStream().size(RtaContract.PREFIX + "logs")));
		metrics.gauge("rta_log_dead_letters", redis,
				r -> read(() -> r.opsForStream().size(RtaContract.PREFIX + "logs:dlq")));
		metrics.gauge("rta_metric_dirty", redis,
				r -> read(() -> r.opsForSet().size(RtaContract.PREFIX + "metric-dirty")));
		metrics.gauge("rta_log_pending", redis, r -> read(
				() -> r.opsForStream().pending(RtaContract.PREFIX + "logs", "sql-writers").getTotalPendingMessages()));
	}

	private static double read(Supplier<Long> supplier) {
		try {
			Long value = supplier.get();
			return value == null ? 0 : value.doubleValue();
		}
		catch (RuntimeException e) {
			return Double.NaN;
		}
	}

}
