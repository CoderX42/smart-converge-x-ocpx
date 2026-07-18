package com.yzytech.scx.tracking.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.dto.OutboxQueueStats;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class QueueMetricsTest {

	@Test
	void exposesOnlyGlobalLowCardinalityQueueStates() {
		CallbackJobMapper callbackMapper = mock(CallbackJobMapper.class);
		EventOutboxMapper outboxMapper = mock(EventOutboxMapper.class);
		CallbackQueueStats callback = new CallbackQueueStats();
		callback.setPendingCount(3L);
		callback.setDeadCount(2L);
		OutboxQueueStats outbox = new OutboxQueueStats();
		outbox.setRetryingCount(4L);
		outbox.setDeadCount(1L);
		when(callbackMapper.selectGlobalStats()).thenReturn(callback);
		when(outboxMapper.selectGlobalStats()).thenReturn(outbox);

		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		new QueueMetrics(callbackMapper, outboxMapper).bindTo(registry);

		assertThat(registry.find("scx.callback.queue").tag("state", "pending").gauge().value()).isEqualTo(3);
		assertThat(registry.find("scx.callback.queue").tag("state", "dead").gauge().value()).isEqualTo(2);
		assertThat(registry.find("scx.outbox.queue").tag("state", "retrying").gauge().value()).isEqualTo(4);
		assertThat(registry.find("scx.outbox.queue").tag("state", "dead").gauge().value()).isEqualTo(1);
		assertThat(registry.find("scx.outbox.queue").meters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
			.noneMatch(tag -> tag.getKey().equals("media_id") || tag.getKey().equals("task_id")));
	}

}
