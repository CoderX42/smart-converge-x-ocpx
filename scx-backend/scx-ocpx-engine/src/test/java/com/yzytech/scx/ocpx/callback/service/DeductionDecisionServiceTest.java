package com.yzytech.scx.ocpx.callback.service;

import com.yzytech.scx.ocpx.shared.redis.RedisHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeductionDecisionServiceTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void usesSameSlotStateAndFactKeysAndParsesTheAtomicDecision() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		RedisHelper legacy = mock(RedisHelper.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		when(legacy.getRouteRatio("link-1", "media-1")).thenReturn(0.8d);
		when(legacy.getRouteOriginalCounts("link-1", "media-1")).thenReturn(new int[RedisHelper.ACTION_CAPACITY]);
		when(legacy.getRouteDeliveredCounts("link-1", "media-1")).thenReturn(new int[RedisHelper.ACTION_CAPACITY]);
		when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn(List.of("PASSED", "0.8", "0", "0"));
		DeductionDecisionService service = new DeductionDecisionService(redis, legacy);

		DeductionDecisionService.Result result = service.decide("fact-1", "link-1", "media-1", 4);

		assertThat(result.decision()).isEqualTo(DeductionDecision.PASSED);
		assertThat(result.ratio()).isEqualByComparingTo("0.800000");
		assertThat(result.replay()).isFalse();
		ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
		verify(redis).execute(any(RedisScript.class), keys.capture(), anyString(), anyString(), anyString(),
				anyString());
		assertThat(keys.getValue()).containsExactly("ocpx:route:deduction:{link-1:media-1}:state",
				"ocpx:route:deduction:{link-1:media-1}:decision:fact-1");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void returnsTheFirstDecisionOnReplayWithoutChangingItsMeaning() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		RedisHelper legacy = mock(RedisHelper.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		when(legacy.getRouteRatio("link-1", "media-1")).thenReturn(0.5d);
		when(legacy.getRouteOriginalCounts("link-1", "media-1")).thenReturn(new int[RedisHelper.ACTION_CAPACITY]);
		when(legacy.getRouteDeliveredCounts("link-1", "media-1")).thenReturn(new int[RedisHelper.ACTION_CAPACITY]);
		when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn(List.of("DEDUCTED", "0.5", "1", "0"));
		DeductionDecisionService service = new DeductionDecisionService(redis, legacy);

		DeductionDecisionService.Result result = service.decide("fact-1", "link-1", "media-1", 0);

		assertThat(result.decision()).isEqualTo(DeductionDecision.DEDUCTED);
		assertThat(result.replay()).isTrue();
	}

}
