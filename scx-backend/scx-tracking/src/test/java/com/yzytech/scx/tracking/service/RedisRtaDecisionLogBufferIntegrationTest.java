package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "SCX_REDIS_IT_PORT", matches = "[0-9]+")
class RedisRtaDecisionLogBufferIntegrationTest {

	private LettuceConnectionFactory connectionFactory;

	private StringRedisTemplate redis;

	private RedisRtaDecisionLogBuffer buffer;

	@BeforeEach
	void setUp() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("127.0.0.1",
				Integer.parseInt(System.getenv("SCX_REDIS_IT_PORT")));
		configuration.setDatabase(14);
		connectionFactory = new LettuceConnectionFactory(configuration);
		connectionFactory.afterPropertiesSet();
		redis = new StringRedisTemplate(connectionFactory);
		redis.afterPropertiesSet();
		redis.getConnectionFactory().getConnection().serverCommands().flushDb();
		TrackingProperties properties = new TrackingProperties();
		properties.setFeedbackPendingIdleMs(20);
		buffer = new RedisRtaDecisionLogBuffer(properties, redis,
				new ObjectMapper().registerModule(new JavaTimeModule()));
	}

	@AfterEach
	void tearDown() {
		if (connectionFactory != null)
			connectionFactory.destroy();
	}

	@Test
	void decisionResultAndLogAreAtomicallyPublished() {
		assertThat(buffer.reserve("task-1", "request-1").status()).isEqualTo(RtaDecisionLogBuffer.Status.NEW);
		buffer.complete("task-1", "request-1", new RtaDecisionLogBuffer.Result("ALLOW", "UPSTREAM_ALLOW"), log());

		RtaDecisionLogBuffer.Reservation duplicate = buffer.reserve("task-1", "request-1");
		assertThat(duplicate.status()).isEqualTo(RtaDecisionLogBuffer.Status.COMPLETED);
		assertThat(duplicate.result().decision()).isEqualTo("ALLOW");
		RtaDecisionLogBuffer.BufferedLog buffered = buffer.poll(1).get(0);
		assertThat(buffered.log().getRequestId()).isEqualTo("request-1");
		buffer.acknowledge(buffered);
		assertThat(
				redis.opsForStream().pending("scx:rta:decision-log", "scx-rta-log-workers").getTotalPendingMessages())
			.isZero();
	}

	@Test
	void inFlightDuplicateFailsClosedWithoutAnotherUpstreamCall() {
		assertThat(buffer.reserve("task-1", "request-2").status()).isEqualTo(RtaDecisionLogBuffer.Status.NEW);

		RtaDecisionLogBuffer.Reservation duplicate = buffer.reserve("task-1", "request-2");

		assertThat(duplicate.status()).isEqualTo(RtaDecisionLogBuffer.Status.PENDING);
		assertThat(duplicate.result().decision()).isEqualTo("DENY");
	}

	@Test
	void secondWorkerAcceptsAnExistingConsumerGroup() {
		buffer.reserve("task-1", "request-3");
		buffer.complete("task-1", "request-3", new RtaDecisionLogBuffer.Result("ALLOW", "UPSTREAM_ALLOW"), log());
		RtaDecisionLogBuffer.BufferedLog delivered = buffer.poll(1).get(0);
		buffer.acknowledge(delivered);

		TrackingProperties properties = new TrackingProperties();
		properties.setFeedbackPendingIdleMs(20);
		RedisRtaDecisionLogBuffer secondWorker = new RedisRtaDecisionLogBuffer(properties, redis,
				new ObjectMapper().registerModule(new JavaTimeModule()));

		assertThat(secondWorker.poll(1)).isEmpty();
	}

	private RtaDecisionLog log() {
		RtaDecisionLog log = new RtaDecisionLog();
		log.setTaskId("task-1");
		log.setEndpointId("rta-1");
		log.setRequestId("request-1");
		log.setRequestHash("a".repeat(64));
		log.setDecision("ALLOW");
		log.setStatus("SUCCEEDED");
		log.setReasonCode("UPSTREAM_ALLOW");
		log.setLatencyMs(3);
		log.setRequestedAt(LocalDateTime.now());
		log.setCompletedAt(LocalDateTime.now());
		return log;
	}

}
