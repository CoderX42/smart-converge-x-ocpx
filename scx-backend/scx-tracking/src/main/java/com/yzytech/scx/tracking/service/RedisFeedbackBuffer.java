package com.yzytech.scx.tracking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scx.tracking", name = "feedback-store", havingValue = "redis")
public class RedisFeedbackBuffer implements FeedbackBuffer {

	private static final String GROUP = "scx-feedback-workers";

	private static final DefaultRedisScript<String> ENQUEUE = new DefaultRedisScript<>("""
			local existing = redis.call('GET', KEYS[1])
			if existing then
			  local separator = string.find(existing, '|')
			  local oldHash = string.sub(existing, 1, separator - 1)
			  local oldReceipt = string.sub(existing, separator + 1)
			  if oldHash == ARGV[1] then return 'DUPLICATE|' .. oldReceipt end
			  return 'CONFLICT|' .. oldReceipt
			end
			redis.call('SET', KEYS[1], ARGV[1] .. '|' .. ARGV[2], 'EX', ARGV[3])
			redis.call('XADD', KEYS[2], '*', 'payload', ARGV[4])
			return 'ACCEPTED|' .. ARGV[2]
			""", String.class);

	private static final DefaultRedisScript<List> AUTOCLAIM = new DefaultRedisScript<>("""
			return redis.call('XAUTOCLAIM', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], 'COUNT', ARGV[5])
			""", List.class);

	private static final DefaultRedisScript<String> DEAD_LETTER = new DefaultRedisScript<>("""
			local dlqId = redis.call('XADD', KEYS[2], '*', 'sourceRecordId', ARGV[2],
			  'receiptId', ARGV[3], 'errorCode', ARGV[4])
			redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
			return dlqId
			""", String.class);

	private static final DefaultRedisScript<String> REPLAY_DEAD_LETTER = new DefaultRedisScript<>("""
			local dlq = redis.call('XRANGE', KEYS[2], ARGV[1], ARGV[1])
			if #dlq == 0 then return 'DLQ_NOT_FOUND' end
			local sourceId = nil
			local dlqFields = dlq[1][2]
			for i = 1, #dlqFields, 2 do
			  if dlqFields[i] == 'sourceRecordId' then sourceId = dlqFields[i + 1] end
			end
			if not sourceId then return 'SOURCE_ID_MISSING' end
			local source = redis.call('XRANGE', KEYS[1], sourceId, sourceId)
			if #source == 0 then return 'SOURCE_NOT_FOUND' end
			local payload = nil
			local sourceFields = source[1][2]
			for i = 1, #sourceFields, 2 do
			  if sourceFields[i] == 'payload' then payload = sourceFields[i + 1] end
			end
			if not payload then return 'PAYLOAD_MISSING' end
			local replayId = redis.call('XADD', KEYS[1], '*', 'payload', payload)
			redis.call('XADD', KEYS[3], '*', 'dlqRecordId', ARGV[1], 'sourceRecordId', sourceId,
			  'replayedRecordId', replayId, 'replayedBy', ARGV[2])
			return replayId
			""", String.class);

	private final TrackingProperties properties;

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper;

	private final String consumerName;

	private final Map<String, Boolean> groups = new ConcurrentHashMap<>();

	private final Map<String, String> reclaimCursors = new ConcurrentHashMap<>();

	public RedisFeedbackBuffer(TrackingProperties properties, StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.properties = properties;
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.consumerName = "worker-" + UUID.randomUUID();
	}

	@Override
	public EnqueueResult enqueue(AcceptedEventCommand command) {
		int partition = Math.floorMod((command.clientId() + ':' + command.deviceKeyHash()).hashCode(),
				properties.getFeedbackStreamPartitions());
		String slot = "{" + partition + "}";
		String stream = "scx:feedback:" + slot + ":stream";
		String eventHash = SignUtils.sha256(command.clientId() + ':' + command.eventId());
		String idempotencyKey = "scx:feedback:" + slot + ":idem:" + eventHash;
		try {
			String payload = objectMapper.writeValueAsString(command);
			String result = redis.execute(ENQUEUE, List.of(idempotencyKey, stream), command.payloadHash(),
					command.receiptId(), String.valueOf(properties.getFeedbackIdempotencyTtlSeconds()), payload);
			if (result == null)
				throw new FeedbackStorageException("Redis 未返回 Feedback 写入结果");
			ensureGroup(stream);
			String[] values = result.split("\\|", 2);
			return new EnqueueResult(Status.valueOf(values[0]), values.length == 2 ? values[1] : command.receiptId());
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Feedback 载荷无法序列化", exception);
		}
		catch (FeedbackStorageException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new FeedbackStorageException("Redis Feedback 写入失败", exception);
		}
	}

	@Override
	public List<BufferedFeedback> poll(int limit) {
		List<BufferedFeedback> result = new ArrayList<>(limit);
		for (int partition = 0; partition < properties.getFeedbackStreamPartitions()
				&& result.size() < limit; partition++) {
			String stream = "scx:feedback:{" + partition + "}:stream";
			ensureGroup(stream);
			try {
				List<MapRecord<String, Object, Object>> reclaimed = reclaim(stream, limit - result.size());
				append(result, reclaimed, null);
				if (result.size() >= limit)
					continue;
				List<MapRecord<String, Object, Object>> records = redis.opsForStream()
					.read(Consumer.from(GROUP, consumerName), StreamReadOptions.empty().count(limit - result.size()),
							StreamOffset.create(stream, ReadOffset.lastConsumed()));
				append(result, records, 1L);
			}
			catch (RuntimeException exception) {
				throw new FeedbackStorageException("Redis Feedback 消费失败", exception);
			}
		}
		return result;
	}

	private List<MapRecord<String, Object, Object>> reclaim(String stream, int limit) {
		String cursor = reclaimCursors.getOrDefault(stream, "0-0");
		List<?> response = redis.execute(AUTOCLAIM, List.of(stream), GROUP, consumerName,
				String.valueOf(properties.getFeedbackPendingIdleMs()), cursor, String.valueOf(limit));
		if (response == null || response.size() < 2)
			return List.of();
		reclaimCursors.put(stream, stringValue(response.get(0)));
		Object entriesValue = response.get(1);
		if (!(entriesValue instanceof List<?> entries))
			return List.of();
		List<MapRecord<String, Object, Object>> records = new ArrayList<>();
		for (Object entryValue : entries) {
			if (!(entryValue instanceof List<?> entry) || entry.size() < 2 || !(entry.get(1) instanceof List<?> fields))
				continue;
			Map<Object, Object> values = new java.util.LinkedHashMap<>();
			for (int index = 0; index + 1 < fields.size(); index += 2)
				values.put(stringValue(fields.get(index)), stringValue(fields.get(index + 1)));
			records.add(MapRecord.create(stream, values).withId(RecordId.of(stringValue(entry.get(0)))));
		}
		return records;
	}

	private void append(List<BufferedFeedback> target, List<MapRecord<String, Object, Object>> records,
			Long defaultDeliveryCount) {
		if (records == null)
			return;
		for (MapRecord<String, Object, Object> record : records) {
			try {
				Object payload = record.getValue().get("payload");
				AcceptedEventCommand command = objectMapper.readValue(String.valueOf(payload),
						AcceptedEventCommand.class);
				long deliveryCount = defaultDeliveryCount == null
						? pendingDeliveryCount(record.getStream(), record.getId()) : defaultDeliveryCount;
				target.add(new BufferedFeedback(record.getStream() + "|" + record.getId().getValue(), command,
						deliveryCount));
			}
			catch (JsonProcessingException exception) {
				deadLetterRaw(record.getStream(), record.getId().getValue(), "unknown", "PAYLOAD_INVALID");
			}
		}
	}

	private long pendingDeliveryCount(String stream, RecordId id) {
		PendingMessages messages = redis.opsForStream()
			.pending(stream, GROUP, Range.closed(id.getValue(), id.getValue()), 1);
		return messages.isEmpty() ? 1 : messages.get(0).getTotalDeliveryCount();
	}

	@Override
	public void acknowledge(BufferedFeedback feedback) {
		String[] value = feedback.bufferId().split("\\|", 2);
		redis.opsForStream().acknowledge(value[0], GROUP, value[1]);
	}

	@Override
	public void release(BufferedFeedback feedback) {
		// Redis Stream keeps an unacknowledged record in the PEL for retry/reclaim.
	}

	@Override
	public void deadLetter(BufferedFeedback feedback, String errorCode) {
		String[] value = feedback.bufferId().split("\\|", 2);
		deadLetterRaw(value[0], value[1], feedback.command().receiptId(), errorCode);
	}

	private void deadLetterRaw(String stream, String recordId, String receiptId, String errorCode) {
		redis.execute(DEAD_LETTER, List.of(stream, stream + ":dlq"), GROUP, recordId, receiptId, errorCode);
	}

	public String replayDeadLetter(String stream, String dlqRecordId, String operatorId) {
		if (!stream.matches("scx:feedback:\\{[0-9]+}:stream") || dlqRecordId == null || operatorId == null)
			throw new IllegalArgumentException("Feedback DLQ 重放参数无效");
		String result = redis.execute(REPLAY_DEAD_LETTER,
				List.of(stream, stream + ":dlq", stream + ":dlq:replay-audit"), dlqRecordId, operatorId);
		if (result == null || !result.matches("[0-9]+-[0-9]+"))
			throw new IllegalArgumentException("Feedback DLQ 重放失败: " + result);
		return result;
	}

	private String stringValue(Object value) {
		if (value instanceof byte[] bytes)
			return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		return String.valueOf(value);
	}

	private void ensureGroup(String stream) {
		if (groups.putIfAbsent(stream, Boolean.TRUE) != null)
			return;
		try {
			redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), GROUP);
		}
		catch (DataAccessException exception) {
			if (!isBusyGroup(exception)) {
				groups.remove(stream);
				throw exception;
			}
		}
	}

	private boolean isBusyGroup(Throwable exception) {
		for (Throwable current = exception; current != null; current = current.getCause()) {
			if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP"))
				return true;
		}
		return false;
	}

}
