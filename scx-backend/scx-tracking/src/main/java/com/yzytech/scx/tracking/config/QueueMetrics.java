package com.yzytech.scx.tracking.config;

import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.dto.OutboxQueueStats;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.ToDoubleFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Exposes global queue depth gauges without media, task, or event identifiers. Operators
 * retain scoped UI statistics; monitoring receives only low-cardinality aggregate state
 * suitable for backlog and DLQ alerts.
 */
@Component
@RequiredArgsConstructor
public class QueueMetrics implements MeterBinder {

	private final CallbackJobMapper callbackJobMapper;

	private final EventOutboxMapper eventOutboxMapper;

	@Override
	public void bindTo(MeterRegistry registry) {
		registerCallback(registry, "pending", CallbackQueueStats::getPendingCount);
		registerCallback(registry, "retrying", CallbackQueueStats::getRetryingCount);
		registerCallback(registry, "paused", CallbackQueueStats::getPausedCount);
		registerCallback(registry, "dead", CallbackQueueStats::getDeadCount);
		registerCallback(registry, "delayed", CallbackQueueStats::getDelayedCount);
		registerOutbox(registry, "pending", OutboxQueueStats::getPendingCount);
		registerOutbox(registry, "retrying", OutboxQueueStats::getRetryingCount);
		registerOutbox(registry, "dead", OutboxQueueStats::getDeadCount);
		registerOutbox(registry, "delayed", OutboxQueueStats::getDelayedCount);
	}

	private void registerCallback(MeterRegistry registry, String state, ToDoubleFunction<CallbackQueueStats> reader) {
		Gauge.builder("scx.callback.queue", () -> callbackValue(reader))
			.tag("state", state)
			.description("SCX callback queue depth by state")
			.register(registry);
	}

	private void registerOutbox(MeterRegistry registry, String state, ToDoubleFunction<OutboxQueueStats> reader) {
		Gauge.builder("scx.outbox.queue", () -> outboxValue(reader))
			.tag("state", state)
			.description("SCX transactional outbox queue depth by state")
			.register(registry);
	}

	private double callbackValue(ToDoubleFunction<CallbackQueueStats> reader) {
		CallbackQueueStats stats = callbackJobMapper.selectGlobalStats();
		return stats == null ? 0 : reader.applyAsDouble(stats);
	}

	private double outboxValue(ToDoubleFunction<OutboxQueueStats> reader) {
		OutboxQueueStats stats = eventOutboxMapper.selectGlobalStats();
		return stats == null ? 0 : reader.applyAsDouble(stats);
	}

}
