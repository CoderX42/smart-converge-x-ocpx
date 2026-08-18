package com.yzytech.scx.ocpx.callback.service;

import com.yzytech.scx.ocpx.shared.redis.RedisHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "ocpx.redis.port", matches = "\\d+")
class DeductionDecisionRedisIntegrationTest {

	private LettuceConnectionFactory connectionFactory;

	private StringRedisTemplate redis;

	private DeductionDecisionService service;

	@BeforeEach
	void setUp() {
		connectionFactory = new LettuceConnectionFactory("127.0.0.1",
				Integer.parseInt(System.getProperty("ocpx.redis.port")));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redis = new StringRedisTemplate(connectionFactory);
		redis.afterPropertiesSet();
		flushDatabase();
		service = new DeductionDecisionService(redis, new RedisHelper(redis));
	}

	@AfterEach
	void tearDown() {
		if (connectionFactory != null) {
			flushDatabase();
			connectionFactory.destroy();
		}
	}

	@Test
	void maintainsExactLongRunCountsForZeroFiftyEightyAndOneHundredPercent() {
		assertLongRunRatio("zero", "0", 0);
		assertLongRunRatio("fifty", "0.5", 50);
		assertLongRunRatio("eighty", "0.8", 80);
		assertLongRunRatio("all", "1", 100);
	}

	@Test
	void keepsActionsAndRoutesIndependentAndReplaysTheOriginalRatioSnapshot() {
		String state = stateKey("link-a", "media-a");
		redis.opsForHash().put(state, "ratio", "0.5");

		DeductionDecisionService.Result first = service.decide("fact-1", "link-a", "media-a", 0);
		redis.opsForHash().put(state, "ratio", "0.8");
		DeductionDecisionService.Result replay = service.decide("fact-1", "link-a", "media-a", 0);
		DeductionDecisionService.Result otherAction = service.decide("fact-2", "link-a", "media-a", 1);
		setRatio("link-b", "media-a", "1");
		DeductionDecisionService.Result otherRoute = service.decide("fact-3", "link-b", "media-a", 0);

		assertThat(first.decision()).isEqualTo(DeductionDecision.DEDUCTED);
		assertThat(first.ratio()).isEqualByComparingTo("0.500000");
		assertThat(replay.decision()).isEqualTo(first.decision());
		assertThat(replay.ratio()).isEqualByComparingTo(first.ratio());
		assertThat(replay.replay()).isTrue();
		assertThat(otherAction.decision()).isEqualTo(DeductionDecision.DEDUCTED);
		assertThat(otherRoute.decision()).isEqualTo(DeductionDecision.PASSED);
		assertThat(redis.opsForHash().get(state, "original:0")).isEqualTo("1");
		assertThat(redis.opsForHash().get(state, "original:1")).isEqualTo("1");
	}

	@Test
	void failsOpenForAnInvalidStoredRatio() {
		for (String invalid : List.of("1.5", "nan", "not-a-number")) {
			String linkId = "link-invalid-" + invalid;
			setRatio(linkId, "media-invalid", invalid);

			DeductionDecisionService.Result result = service.decide("fact-" + invalid, linkId, "media-invalid", 0);

			assertThat(result.decision()).isEqualTo(DeductionDecision.PASSED);
			assertThat(result.ratio()).isEqualByComparingTo("1.000000");
			assertThat(redis.opsForHash().get(stateKey(linkId, "media-invalid"), "ratio")).isEqualTo("1");
		}
	}

	@Test
	void doesNotLoseCountsUnderConcurrentCallbacks() throws Exception {
		setRatio("link-concurrent", "media-concurrent", "0.8");
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(12);
		try {
			List<Future<DeductionDecisionService.Result>> futures = new ArrayList<>();
			for (int index = 0; index < 200; index++) {
				String factId = "concurrent-" + index;
				futures.add(executor.submit(() -> {
					start.await();
					return service.decide(factId, "link-concurrent", "media-concurrent", 0);
				}));
			}
			start.countDown();
			long passed = 0;
			for (Future<DeductionDecisionService.Result> future : futures) {
				if (future.get().decision() == DeductionDecision.PASSED) {
					passed++;
				}
			}
			assertThat(passed).isEqualTo(160);
		}
		finally {
			executor.shutdownNow();
		}
		String state = stateKey("link-concurrent", "media-concurrent");
		assertThat(redis.opsForHash().get(state, "original:0")).isEqualTo("200");
		assertThat(redis.opsForHash().get(state, "passed:0")).isEqualTo("160");
	}

	private void assertLongRunRatio(String route, String ratio, int expectedPassed) {
		setRatio(route, "media", ratio);
		int passed = 0;
		for (int index = 0; index < 100; index++) {
			if (service.decide(route + "-" + index, route, "media", 0).decision() == DeductionDecision.PASSED) {
				passed++;
			}
		}
		assertThat(passed).isEqualTo(expectedPassed);
		assertThat(redis.opsForHash().get(stateKey(route, "media"), "original:0")).isEqualTo("100");
		assertThat(redis.opsForHash().get(stateKey(route, "media"), "passed:0"))
			.isEqualTo(String.valueOf(expectedPassed));
	}

	private void setRatio(String linkId, String mediaId, String ratio) {
		redis.opsForHash().put(stateKey(linkId, mediaId), "ratio", ratio);
	}

	private String stateKey(String linkId, String mediaId) {
		return "ocpx:route:deduction:{" + linkId + ":" + mediaId + "}:state";
	}

	private void flushDatabase() {
		try (RedisConnection connection = connectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}

}
