package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.EventOutbox;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scx.tracking", name = "outbox-publisher-enabled", havingValue = "true")
public class RocketMqOutboxPublisher implements OutboxPublisher {

	private final RocketMQTemplate rocketMqTemplate;

	private final TrackingProperties trackingProperties;

	@Override
	public void publish(EventOutbox event) {
		rocketMqTemplate.syncSend(
				trackingProperties.getOutboxTopic(), Map.of("eventId", event.getId(), "aggregateId",
						event.getAggregateId(), "eventType", event.getEventType(), "payload", event.getPayloadJson()),
				5000);
	}

}
