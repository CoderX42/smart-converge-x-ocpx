package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.FeedbackEnvelope;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.DecoratedRedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.IntegerOutput;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class RedisFeedbackIngress {

	private static final DefaultRedisScript<String> ENQUEUE_SCRIPT = new DefaultRedisScript<>("""
			local existing = redis.call('GET', KEYS[1])
			if existing then
			  if existing == ARGV[1] then
			    redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
			    return 'DUPLICATE'
			  end
				  return 'CONFLICT'
				end
			local recordId = redis.call('XADD', KEYS[2], '*', 'event_id', ARGV[3], 'envelope', ARGV[4])
			redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
				return 'ACCEPTED:' .. recordId
				""", String.class);

	private final StringRedisTemplate redisTemplate;

	private final ObjectMapper objectMapper;

	private final OcpxRuntimeProperties properties;

	public RedisFeedbackIngress(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
			OcpxRuntimeProperties properties) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	public IngressResult enqueue(FeedbackEnvelope envelope) {
		try {
			int partition = partition(envelope.eventId());
			String stream = streamKey(partition);
			String idempotencyKey = partitionPrefix(partition) + ":idem:" + envelope.channelCode() + ":"
					+ envelope.eventId();
			String serialized = objectMapper.writeValueAsString(envelope);
			IngressResult result = redisTemplate.execute(new SessionCallback<>() {
				@Override
				@SuppressWarnings("unchecked")
				public <K, V> IngressResult execute(RedisOperations<K, V> rawOperations) {
					RedisOperations<String, String> operations = (RedisOperations<String, String>) rawOperations;
					String state = operations.execute(ENQUEUE_SCRIPT, List.of(idempotencyKey, stream),
							envelope.payloadHash(), Long.toString(properties.getIdempotencyTtl().toMillis()),
							envelope.eventId(), serialized);
					return result(envelope.eventId(), state);
				}
			});
			if (result == null) {
				throw new OcpxException(HttpStatus.SERVICE_UNAVAILABLE, "feedback queue unavailable");
			}
			return result;
		}
		catch (JsonProcessingException | DataAccessException e) {
			throw new OcpxException(HttpStatus.SERVICE_UNAVAILABLE, "feedback queue unavailable");
		}
	}

	private IngressResult result(String eventId, String state) {
		if (state == null) {
			throw new OcpxException(HttpStatus.SERVICE_UNAVAILABLE, "feedback queue unavailable");
		}
		if (state.startsWith("ACCEPTED:") || "DUPLICATE".equals(state)) {
			awaitReplicaIfRequired();
			return new IngressResult(eventId,
					state.startsWith("ACCEPTED:") ? IngressResult.Status.ACCEPTED : IngressResult.Status.DUPLICATE,
					OffsetDateTime.now());
		}
		return new IngressResult(eventId, IngressResult.Status.CONFLICT, OffsetDateTime.now());
	}

	public String streamKey(int partition) {
		return partitionPrefix(partition) + ":stream";
	}

	public String dlqKey(int partition) {
		return partitionPrefix(partition) + ":dlq";
	}

	private String partitionPrefix(int partition) {
		return "scx:ocpx:{feedback:" + partition + "}";
	}

	private int partition(String eventId) {
		return Math.floorMod(eventId.hashCode(), properties.getPartitionCount());
	}

	private void awaitReplicaIfRequired() {
		if (properties.getReplicaAcksRequired() <= 0) {
			return;
		}
		Long acknowledged = redisTemplate.execute((RedisCallback<Long>) this::waitForReplica, true);
		if (acknowledged == null || acknowledged < properties.getReplicaAcksRequired()) {
			throw new OcpxException(HttpStatus.SERVICE_UNAVAILABLE, "feedback queue replica quorum unavailable");
		}
	}

	private Long waitForReplica(RedisConnection connection) {
		byte[] replicas = Integer.toString(properties.getReplicaAcksRequired()).getBytes(StandardCharsets.UTF_8);
		byte[] timeout = Long.toString(properties.getReplicaAckTimeoutMs()).getBytes(StandardCharsets.UTF_8);
		RedisConnection delegate = connection;
		while (delegate instanceof DecoratedRedisConnection decorated) {
			delegate = decorated.getDelegate();
		}
		Object result;
		if (delegate instanceof LettuceConnection lettuce) {
			result = lettuce.execute("WAIT", new IntegerOutput<>(ByteArrayCodec.INSTANCE), replicas, timeout);
		}
		else {
			result = delegate.execute("WAIT", replicas, timeout);
		}
		if (result instanceof Number value) {
			return value.longValue();
		}
		if (result instanceof byte[] value) {
			return Long.parseLong(new String(value, StandardCharsets.UTF_8));
		}
		return 0L;
	}

}
