package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.config.AdxTrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdxConfigOutboxPublisherTest {

	@Test
	void testAndDefaultProfilesFailClosedWithoutTouchingRedisOrDatabase() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		AdxTrackingProperties properties = new AdxTrackingProperties();
		properties.setDistributionWorkerEnabled(false);

		new AdxConfigOutboxPublisher(jdbc, redis, properties).publishPending();

		verifyNoInteractions(jdbc, redis);
	}

	@Test
	void retryBackoffIsBoundedAndDeterministic() {
		assertThat(AdxConfigOutboxPublisher.retryDelaySeconds(0)).isEqualTo(1);
		assertThat(AdxConfigOutboxPublisher.retryDelaySeconds(3)).isEqualTo(8);
		assertThat(AdxConfigOutboxPublisher.retryDelaySeconds(30)).isEqualTo(256);
	}

}
