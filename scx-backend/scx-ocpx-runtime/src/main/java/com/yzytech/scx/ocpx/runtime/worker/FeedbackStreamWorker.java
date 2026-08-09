package com.yzytech.scx.ocpx.runtime.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.ingress.RedisFeedbackIngress;
import com.yzytech.scx.ocpx.runtime.model.FeedbackEnvelope;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@SuppressWarnings("unchecked")
public class FeedbackStreamWorker {

	private static final Logger log = LoggerFactory.getLogger(FeedbackStreamWorker.class);

	private static final String GROUP = "scx-ocpx-persist";

	private final StringRedisTemplate redisTemplate;

	private final ObjectMapper objectMapper;

	private final OcpxRuntimeProperties properties;

	private final RedisFeedbackIngress ingress;

	private final OcpxPersistenceRepository repository;

	private final String consumerName = UUID.randomUUID().toString();

	public FeedbackStreamWorker(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
			OcpxRuntimeProperties properties, RedisFeedbackIngress ingress, OcpxPersistenceRepository repository) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.ingress = ingress;
		this.repository = repository;
	}

	@PostConstruct
	void createGroups() {
		for (int partition = 0; partition < properties.getPartitionCount(); partition++) {
			String key = ingress.streamKey(partition);
			try {
				redisTemplate.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
						"CREATE".getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8),
						GROUP.getBytes(StandardCharsets.UTF_8), "0-0".getBytes(StandardCharsets.UTF_8),
						"MKSTREAM".getBytes(StandardCharsets.UTF_8)));
			}
			catch (Exception e) {
				// Spring Data 会把 RedisBusyException 包装成“Error in execution”，所以必须检查完整 cause 链。
				// Consumer Group 已存在是多实例启动和滚动发布时的正常幂等结果，不应阻止应用启动。
				if (!isBusyGroup(e)) {
					throw e;
				}
			}
		}
	}

	static boolean isBusyGroup(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	@Scheduled(fixedDelayString = "${scx.ocpx.poll-delay-ms:20}")
	public void consume() {
		for (int partition = 0; partition < properties.getPartitionCount(); partition++) {
			String key = ingress.streamKey(partition);
			List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
				.read(Consumer.from(GROUP, consumerName), StreamReadOptions.empty().count(properties.getBatchSize()),
						StreamOffset.create(key, ReadOffset.lastConsumed()));
			persistAndAcknowledge(key, partition, records);
		}
	}

	@Scheduled(fixedDelay = 30000, initialDelay = 30000)
	public void reclaimPending() {
		for (int partition = 0; partition < properties.getPartitionCount(); partition++) {
			String key = ingress.streamKey(partition);
			PendingMessages pending = redisTemplate.opsForStream()
				.pending(key, GROUP, Range.unbounded(), properties.getBatchSize());
			if (pending == null || pending.isEmpty()) {
				continue;
			}
			List<RecordId> claimable = new ArrayList<>();
			for (PendingMessage message : pending) {
				if (message.getElapsedTimeSinceLastDelivery().compareTo(properties.getPendingIdle()) < 0) {
					continue;
				}
				if (message.getTotalDeliveryCount() >= properties.getMaxDeliveryAttempts()) {
					moveToDlq(key, partition, message.getId());
				}
				else {
					claimable.add(message.getId());
				}
			}
			if (!claimable.isEmpty()) {
				List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream()
					.claim(key, GROUP, consumerName, properties.getPendingIdle(), claimable.toArray(RecordId[]::new));
				persistAndAcknowledge(key, partition, claimed);
			}
		}
	}

	private void persistAndAcknowledge(String key, int partition, List<MapRecord<String, Object, Object>> records) {
		if (records == null || records.isEmpty()) {
			return;
		}
		List<FeedbackEnvelope> envelopes = new ArrayList<>();
		List<RecordId> validRecordIds = new ArrayList<>();
		for (MapRecord<String, Object, Object> record : records) {
			try {
				envelopes.add(parse(record));
				validRecordIds.add(record.getId());
			}
			catch (RuntimeException e) {
				moveInvalidToDlq(key, partition, record, e.getMessage());
			}
		}
		if (envelopes.isEmpty()) {
			return;
		}
		try {
			repository.persistFeedbackBatch(envelopes);
			redisTemplate.opsForStream().acknowledge(key, GROUP, validRecordIds.toArray(RecordId[]::new));
			redisTemplate.opsForStream().delete(key, validRecordIds.toArray(RecordId[]::new));
		}
		catch (Exception e) {
			log.warn("OCPX feedback batch persistence failed: partition={}, records={}", partition, records.size());
		}
	}

	private void moveInvalidToDlq(String key, int partition, MapRecord<String, Object, Object> record, String reason) {
		String envelope = String.valueOf(record.getValue().get("envelope"));
		String eventId = String.valueOf(record.getValue().get("event_id"));
		repository.persistIngressDlq(partition, record.getId().getValue(), eventId, envelope,
				"INVALID_ENVELOPE:" + reason);
		redisTemplate.opsForStream().add(ingress.dlqKey(partition), record.getValue());
		redisTemplate.opsForStream().trim(ingress.dlqKey(partition), 100_000, true);
		redisTemplate.opsForStream().acknowledge(key, GROUP, record.getId());
		redisTemplate.opsForStream().delete(key, record.getId());
	}

	private FeedbackEnvelope parse(MapRecord<String, Object, Object> record) {
		try {
			Object value = record.getValue().get("envelope");
			return objectMapper.readValue(String.valueOf(value), FeedbackEnvelope.class);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("invalid feedback stream envelope", e);
		}
	}

	private void moveToDlq(String key, int partition, RecordId recordId) {
		List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
			.claim(key, GROUP, consumerName, Duration.ZERO, recordId);
		if (records == null || records.isEmpty()) {
			return;
		}
		MapRecord<String, Object, Object> record = records.get(0);
		String envelope = String.valueOf(record.getValue().get("envelope"));
		String eventId = String.valueOf(record.getValue().get("event_id"));
		repository.persistIngressDlq(partition, recordId.getValue(), eventId, envelope, "MAX_DELIVERY_ATTEMPTS");
		redisTemplate.opsForStream().add(ingress.dlqKey(partition), record.getValue());
		redisTemplate.opsForStream().trim(ingress.dlqKey(partition), 100_000, true);
		redisTemplate.opsForStream().acknowledge(key, GROUP, recordId);
		redisTemplate.opsForStream().delete(key, recordId);
	}

}
