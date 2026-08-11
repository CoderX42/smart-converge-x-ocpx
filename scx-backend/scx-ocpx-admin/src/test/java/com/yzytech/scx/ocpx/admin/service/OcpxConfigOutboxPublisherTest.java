package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OcpxConfigOutboxPublisherTest {

	@Test
	void testAndDefaultProfilesFailClosedWithoutTouchingRedisOrDatabase() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		OcpxTrackingProperties properties = new OcpxTrackingProperties();
		properties.setDistributionWorkerEnabled(false);

		new OcpxConfigOutboxPublisher(jdbc, redis, properties).publishPending();

		verifyNoInteractions(jdbc, redis);
	}

	@Test
	void retryBackoffIsBoundedAndDeterministic() {
		assertThat(OcpxConfigOutboxPublisher.retryDelaySeconds(0)).isEqualTo(1);
		assertThat(OcpxConfigOutboxPublisher.retryDelaySeconds(3)).isEqualTo(8);
		assertThat(OcpxConfigOutboxPublisher.retryDelaySeconds(30)).isEqualTo(256);
	}

}
