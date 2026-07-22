package com.yzytech.scx.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRtaDecisionLogBuffer implements RtaDecisionLogBuffer {

	private static final String STREAM = "scx:rta:decision-log";

	private static final String DLQ = STREAM + ":dlq";

	private static final String GROUP = "scx-rta-log-workers";

	private static final DefaultRedisScript<String> RESERVE = new DefaultRedisScript<>("""
			local existing = redis.call('GET', KEYS[1])
			if existing then return existing end
			redis.call('SET', KEYS[1], 'PENDING', 'EX', ARGV[1], 'NX')
			return 'NEW'
			""", String.class);

	private static final DefaultRedisScript<String> COMPLETE = new DefaultRedisScript<>("""
			redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
			return redis.call('XADD', KEYS[2], '*', 'payload', ARGV[3])
			""", String.class);

	private static final DefaultRedisScript<List> AUTOCLAIM = new DefaultRedisScript<>("""
			return redis.call('XAUTOCLAIM', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], 'COUNT', ARGV[5])
			""", List.class);

	private static final DefaultRedisScript<String> DEAD_LETTER = new DefaultRedisScript<>("""
			local dlqId = redis.call('XADD', KEYS[2], '*', 'sourceRecordId', ARGV[2], 'errorCode', ARGV[3])
			redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
			return dlqId
			""", String.class);

	private final TrackingProperties properties;

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper;

	private final String consumerName = "worker-" + UUID.randomUUID();

	private String reclaimCursor = "0-0";

	private volatile boolean groupReady;

	public RedisRtaDecisionLogBuffer(TrackingProperties properties, StringRedisTemplate redis,
			ObjectMapper objectMapper) {
		this.properties = properties;
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	@Override
	public Reservation reserve(String taskId, String requestId) {
		String value = redis.execute(RESERVE, List.of(idempotencyKey(taskId, requestId)),
				String.valueOf(properties.getRtaReservationTtlSeconds()));
		if ("NEW".equals(value))
			return new Reservation(Status.NEW, null);
		if ("PENDING".equals(value))
			return new Reservation(Status.PENDING, new Result("DENY", "REQUEST_IN_PROGRESS"));
		try {
			return new Reservation(Status.COMPLETED, objectMapper.readValue(value, Result.class));
		}
		catch (Exception exception) {
			throw new FeedbackStorageException("Redis RTA 幂等结果无效", exception);
		}
	}

	@Override
	public void complete(String taskId, String requestId, Result result, RtaDecisionLog log) {
		try {
			redis.execute(COMPLETE, List.of(idempotencyKey(taskId, requestId), STREAM),
					objectMapper.writeValueAsString(result), String.valueOf(properties.getRtaResultTtlSeconds()),
					objectMapper.writeValueAsString(log));
			ensureGroup();
		}
		catch (Exception exception) {
			throw new FeedbackStorageException("Redis RTA 决策日志写入失败", exception);
		}
	}

	@Override
	public List<BufferedLog> poll(int limit) {
		ensureGroup();
		List<BufferedLog> result = new ArrayList<>();
		append(result, reclaim(limit), null);
		if (result.size() < limit) {
			List<MapRecord<String, Object, Object>> records = redis.opsForStream()
				.read(Consumer.from(GROUP, consumerName), StreamReadOptions.empty().count(limit - result.size()),
						StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
			append(result, records, 1L);
		}
		return result;
	}

	@Override
	public void acknowledge(BufferedLog log) {
		redis.opsForStream().acknowledge(STREAM, GROUP, log.recordId());
	}

	@Override
	public void deadLetter(BufferedLog log, String errorCode) {
		redis.execute(DEAD_LETTER, List.of(STREAM, DLQ), GROUP, log.recordId(), errorCode);
	}

	private List<MapRecord<String, Object, Object>> reclaim(int limit) {
		List<?> response = redis.execute(AUTOCLAIM, List.of(STREAM), GROUP, consumerName,
				String.valueOf(properties.getFeedbackPendingIdleMs()), reclaimCursor, String.valueOf(limit));
		if (response == null || response.size() < 2)
			return List.of();
		reclaimCursor = stringValue(response.get(0));
		if (!(response.get(1) instanceof List<?> entries))
			return List.of();
		List<MapRecord<String, Object, Object>> records = new ArrayList<>();
		for (Object entryValue : entries) {
			if (!(entryValue instanceof List<?> entry) || entry.size() < 2 || !(entry.get(1) instanceof List<?> fields))
				continue;
			Map<Object, Object> values = new LinkedHashMap<>();
			for (int index = 0; index + 1 < fields.size(); index += 2)
				values.put(stringValue(fields.get(index)), stringValue(fields.get(index + 1)));
			records.add(MapRecord.create(STREAM, values).withId(RecordId.of(stringValue(entry.get(0)))));
		}
		return records;
	}

	private void append(List<BufferedLog> target, List<MapRecord<String, Object, Object>> records,
			Long defaultDeliveryCount) {
		if (records == null)
			return;
		for (MapRecord<String, Object, Object> record : records) {
			try {
				RtaDecisionLog log = objectMapper.readValue(String.valueOf(record.getValue().get("payload")),
						RtaDecisionLog.class);
				long count = defaultDeliveryCount == null ? deliveryCount(record.getId()) : defaultDeliveryCount;
				target.add(new BufferedLog(record.getId().getValue(), log, count));
			}
			catch (Exception exception) {
				redis.execute(DEAD_LETTER, List.of(STREAM, DLQ), GROUP, record.getId().getValue(), "PAYLOAD_INVALID");
			}
		}
	}

	private long deliveryCount(RecordId id) {
		var messages = redis.opsForStream().pending(STREAM, GROUP, Range.closed(id.getValue(), id.getValue()), 1);
		return messages.isEmpty() ? 1 : messages.get(0).getTotalDeliveryCount();
	}

	private void ensureGroup() {
		if (groupReady)
			return;
		try {
			redis.opsForStream().createGroup(STREAM, ReadOffset.from("0-0"), GROUP);
		}
		catch (DataAccessException exception) {
			if (!isBusyGroup(exception))
				throw exception;
		}
		groupReady = true;
	}

	private boolean isBusyGroup(Throwable exception) {
		for (Throwable current = exception; current != null; current = current.getCause()) {
			if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP"))
				return true;
		}
		return false;
	}

	private String idempotencyKey(String taskId, String requestId) {
		return "scx:rta:idem:" + SignUtils.sha256(taskId + ':' + requestId);
	}

	private String stringValue(Object value) {
		return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
	}

}
