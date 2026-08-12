package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.constant.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxConfigRefreshPublisherTest {

	@Test
	@SuppressWarnings("unchecked")
	void writesTheSharedLegacyRefreshFlag() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);

		new OcpxConfigRefreshPublisher(redis).mark();

		verify(values).set(RedisConstants.DATA_REFRESH, "1");
	}

}
