package com.yzytech.scx.ocpx.rta.stream;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.rta.config.RtaProperties;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionEvent;
import com.yzytech.scx.ocpx.rta.persistence.RtaDecisionFactPersistence;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;



/** Redis Stream 消费者：重试次数使用 Redis Pending 投递计数，进程重启不重置。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.rta.enabled", havingValue = "true")
public class RtaDecisionFactWorker {

	private final StringRedisTemplate redis;

	private final RtaDecisionFactPersistence persistence;

	private final RtaProperties properties;

	private final String consumerName = "rta-" + UUID.randomUUID().toString().replace("-", "");

	private final AtomicLong pendingCount = new AtomicLong();

	private final AtomicLong dlqCount = new AtomicLong();

	private final AtomicLong lagMillis = new AtomicLong();

	private MeterRegistry meterRegistry;

	public RtaDecisionFactWorker(StringRedisTemplate redis, RtaDecisionFactPersistence persistence,
			RtaProperties properties) {
		this.redis = redis;
		this.persistence = persistence;
		this.properties = properties;
	}

	@Autowired(required = false)
	void setMeterRegistry(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		meterRegistry.gauge("rta_stream_pending", pendingCount);
		meterRegistry.gauge("rta_dlq", dlqCount);
		meterRegistry.gauge("rta_fact_persistence_lag_ms", lagMillis);
	}

	@Scheduled(fixedDelayString = "${app.rta.worker-delay-ms:1000}")
	public void consume() {
		try {
			ensureGroup();
			List<MapRecord<String, String, String>> records = claimPending();
			if (records.isEmpty()) {
				StreamOffset<String> offset = StreamOffset.create(properties.getStreamKey(), ReadOffset.lastConsumed());
				List<MapRecord<String, String, String>> incoming = readIncoming(offset);
				records = incoming == null ? List.of() : incoming;
			}
			for (MapRecord<String, String, String> record : records) {
				process(record);
			}
			var pending = redis.opsForStream().pending(properties.getStreamKey(), properties.getConsumerGroup());
			pendingCount.set(pending == null ? 0 : pending.getTotalPendingMessages());
			Long size = redis.opsForStream().size(properties.getStreamKey() + ":dlq");
			dlqCount.set(size == null ? 0 : size);
		}
		catch (RuntimeException exception) {
			// Do not log Redis commands or payloads contained in exception messages.
			log.warn("RTA decision fact consume failed type:{}", exception.getClass().getSimpleName());
		}
	}

	private void ensureGroup() {
		if (Boolean.FALSE.equals(redis.hasKey(properties.getStreamKey()))) {
			redis.opsForStream().add(properties.getStreamKey(), Map.of("payload", "{}"));
		}
		try {
			redis.opsForStream().createGroup(properties.getStreamKey(), ReadOffset.from("0-0"),
					properties.getConsumerGroup());
		}
		catch (RuntimeException exception) {
			if (!String.valueOf(exception.getMessage()).toUpperCase().contains("BUSYGROUP")) {
				throw exception;
			}
		}
	}

	private List<MapRecord<String, String, String>> claimPending() {
		var pending = redis.opsForStream().pending(properties.getStreamKey(), properties.getConsumerGroup(),
				Range.unbounded(), Math.max(1, properties.getBatchSize()));
		List<RecordId> ids = new ArrayList<>();
		for (PendingMessage message : pending) {
			if (message.getElapsedTimeSinceLastDelivery()
				.compareTo(Duration.ofSeconds(Math.max(1, properties.getClaimIdleSeconds()))) >= 0) {
				ids.add(message.getId());
			}
		}
		if (ids.isEmpty()) {
			return List.of();
		}
		return claimRecords(ids);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List<MapRecord<String, String, String>> readIncoming(StreamOffset<String> offset) {
		return (List) redis.opsForStream().read(Consumer.from(properties.getConsumerGroup(), consumerName),
				StreamReadOptions.empty().count(Math.max(1, properties.getBatchSize())), offset);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List<MapRecord<String, String, String>> claimRecords(List<RecordId> ids) {
		return (List) redis.opsForStream().claim(properties.getStreamKey(), properties.getConsumerGroup(), consumerName,
				Duration.ofSeconds(Math.max(1, properties.getClaimIdleSeconds())), ids.toArray(RecordId[]::new));
	}

	void process(MapRecord<String, String, String> record) {
		String payload = record.getValue().get("payload");
		try {
			if ("{}".equals(payload)) {
				ack(record);
				return;
			}
			RtaDecisionEvent event = JSON.parseObject(payload, RtaDecisionEvent.class);
			persistence.persist(event);
			lagMillis.set(Math.max(0, Duration.between(event.getCompletedAt(),
					LocalDateTime.now(ZoneId.of("Asia/Shanghai"))).toMillis()));
			ack(record);
		}
		catch (RuntimeException exception) {
			if (meterRegistry != null) {
				meterRegistry.counter("rta_fact_persistence_failures_total").increment();
			}
			var pending = redis.opsForStream().pending(properties.getStreamKey(), properties.getConsumerGroup(),
				Range.closed(record.getId().getValue(), record.getId().getValue()), 1);
			for (PendingMessage message : pending) {
				if (message.getTotalDeliveryCount() > Math.max(0, properties.getMaxRetries())) {
					RecordId dlqId = redis.opsForStream().add(properties.getStreamKey() + ":dlq",
							Map.of("payload", payload == null ? "{}" : payload,
									"error", exception.getClass().getSimpleName()));
					if (dlqId == null) {
						throw new IllegalStateException("RTA DLQ returned no record id");
					}
					ack(record);
					return;
				}
			}
			log.warn("RTA fact persistence pending record:{}", record.getId());
		}
	}

	private void ack(MapRecord<String, String, String> record) {
		redis.opsForStream().acknowledge(properties.getStreamKey(), properties.getConsumerGroup(), record.getId());
	}

}
