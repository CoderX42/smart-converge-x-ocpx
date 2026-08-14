package com.yzytech.scx.ocpx.shared.redis;

import com.yzytech.scx.ocpx.shared.constant.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisHelperTest {

	@Test
	@SuppressWarnings("unchecked")
	void usesTheOcpxRouteKeySpaceWithLinkAndMediaDimensions() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.get("ocpx:route:ratio:link-id:media-id")).thenReturn("0.5");
		when(values.get("ocpx:route:original-count:link-id:media-id")).thenReturn("[1,0,0,0,0,0,0,0,0,0,0]");
		when(values.get("ocpx:route:delivered-count:link-id:media-id")).thenReturn("[0,1,0,0,0,0,0,0,0,0,0]");
		RedisHelper helper = new RedisHelper(redis);

		assertThat(helper.getRouteRatio("link-id", "media-id")).isEqualTo(0.5);
		assertThat(helper.getRouteOriginalCounts("link-id", "media-id")).hasSize(19).startsWith(1);
		assertThat(helper.getRouteDeliveredCounts("link-id", "media-id")).hasSize(19).startsWith(0, 1);
		helper.setRouteOriginalCounts("link-id", "media-id", new int[11]);
		helper.setRouteDeliveredCounts("link-id", "media-id", new int[11]);

		verify(values).get(RedisConstants.ROUTE_RATIO_PREFIX + "link-id:media-id");
		verify(values).get(RedisConstants.ROUTE_ORIGINAL_COUNT_PREFIX + "link-id:media-id");
		verify(values).get(RedisConstants.ROUTE_DELIVERED_COUNT_PREFIX + "link-id:media-id");
		verify(values).set(RedisConstants.ROUTE_ORIGINAL_COUNT_PREFIX + "link-id:media-id",
				"[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]");
		verify(values).set(RedisConstants.ROUTE_DELIVERED_COUNT_PREFIX + "link-id:media-id",
				"[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]");
	}

}
