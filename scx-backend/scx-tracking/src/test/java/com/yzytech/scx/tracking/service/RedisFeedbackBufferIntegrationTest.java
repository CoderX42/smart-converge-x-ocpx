package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "SCX_REDIS_IT_PORT", matches = "[0-9]+")
class RedisFeedbackBufferIntegrationTest {

	private LettuceConnectionFactory connectionFactory;

	private StringRedisTemplate redis;

	private TrackingProperties properties;

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("127.0.0.1",
				Integer.parseInt(System.getenv("SCX_REDIS_IT_PORT")));
		configuration.setDatabase(15);
		connectionFactory = new LettuceConnectionFactory(configuration);
		connectionFactory.afterPropertiesSet();
		redis = new StringRedisTemplate(connectionFactory);
		redis.afterPropertiesSet();
		redis.getConnectionFactory().getConnection().serverCommands().flushDb();
		properties = new TrackingProperties();
		properties.setFeedbackStore("redis");
		properties.setFeedbackStreamPartitions(1);
		properties.setFeedbackPendingIdleMs(50);
		properties.setFeedbackIdempotencyTtlSeconds(300);
		objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
	}

	@AfterEach
	void tearDown() {
		if (connectionFactory != null)
			connectionFactory.destroy();
	}

	@Test
	void anotherConsumerReclaimsPendingThenAcknowledges() throws Exception {
		RedisFeedbackBuffer first = new RedisFeedbackBuffer(properties, redis, objectMapper);
		assertThat(first.enqueue(command("event-1", "receipt-1")).status()).isEqualTo(FeedbackBuffer.Status.ACCEPTED);
		FeedbackBuffer.BufferedFeedback pending = first.poll(1).get(0);
		assertThat(pending.deliveryCount()).isEqualTo(1);

		Thread.sleep(80);
		RedisFeedbackBuffer restarted = new RedisFeedbackBuffer(properties, redis, objectMapper);
		FeedbackBuffer.BufferedFeedback reclaimed = restarted.poll(1).get(0);
		assertThat(reclaimed.command().receiptId()).isEqualTo("receipt-1");
		assertThat(reclaimed.deliveryCount()).isGreaterThanOrEqualTo(2);
		restarted.acknowledge(reclaimed);
		assertThat(redis.opsForStream()
			.pending("scx:feedback:{0}:stream", "scx-feedback-workers")
			.getTotalPendingMessages()).isZero();
	}

	@Test
	void deadLetterWriteAndAckAreObservedTogether() {
		RedisFeedbackBuffer buffer = new RedisFeedbackBuffer(properties, redis, objectMapper);
		buffer.enqueue(command("event-2", "receipt-2"));
		FeedbackBuffer.BufferedFeedback feedback = buffer.poll(1).get(0);

		buffer.deadLetter(feedback, "PERSISTENCE_RETRY_EXHAUSTED");

		assertThat(redis.opsForStream()
			.pending("scx:feedback:{0}:stream", "scx-feedback-workers")
			.getTotalPendingMessages()).isZero();
		assertThat(redis.opsForStream().size("scx:feedback:{0}:stream:dlq")).isEqualTo(1);
		String dlqRecordId = redis.opsForStream()
			.range("scx:feedback:{0}:stream:dlq", org.springframework.data.domain.Range.unbounded())
			.get(0)
			.getId()
			.getValue();

		String replayedId = buffer.replayDeadLetter("scx:feedback:{0}:stream", dlqRecordId, "uat-operator");

		assertThat(replayedId).matches("[0-9]+-[0-9]+");
		assertThat(redis.opsForStream().size("scx:feedback:{0}:stream:dlq:replay-audit")).isEqualTo(1);
		assertThat(buffer.poll(1).get(0).command().receiptId()).isEqualTo("receipt-2");
	}

	private AcceptedEventCommand command(String eventId, String receiptId) {
		return new AcceptedEventCommand(receiptId, "client-1", "PARTNER", eventId, "TASK-1", "CLICK",
				OffsetDateTime.now(), OffsetDateTime.now(), "ANDROID", "OAID", null, "v1", "a".repeat(64), null,
				"b".repeat(64), "{}", null, null, null, null, null);
	}

}
