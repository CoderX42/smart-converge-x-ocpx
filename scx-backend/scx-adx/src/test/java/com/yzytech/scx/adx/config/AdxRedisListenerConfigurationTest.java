package com.yzytech.scx.adx.config;

import com.yzytech.scx.adx.service.AdxAccessScope;
import com.yzytech.scx.adx.service.AdxRouteDistributionApplyWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdxRedisListenerConfigurationTest {

	@Test
	void usesTheManagementListenerContainerWhenOcpxContainerIsAlsoPresent() {
		RedisMessageListenerContainer managementContainer = mock(RedisMessageListenerContainer.class);
		RedisMessageListenerContainer ocpxContainer = mock(RedisMessageListenerContainer.class);

		new ApplicationContextRunner()
			.withPropertyValues("scx.adx.tracking.distribution-worker-enabled=true")
			.withBean("redisMessageListenerContainer", RedisMessageListenerContainer.class, () -> managementContainer)
			.withBean("ocpxRedisMessageListenerContainer", RedisMessageListenerContainer.class, () -> ocpxContainer)
			.withBean(AdxRouteDistributionApplyWorker.class, () -> mock(AdxRouteDistributionApplyWorker.class))
			.withBean(AdxTrackingProperties.class, () -> mock(AdxTrackingProperties.class))
			.withBean(AdxAccessScope.class, () -> mock(AdxAccessScope.class))
			.withUserConfiguration(AdxRouteDistributionRedisListenerConfiguration.class,
					AdxScopeRedisListenerConfiguration.class)
			.run(context -> assertThat(context).hasNotFailed()
				.hasSingleBean(AdxRouteDistributionRedisListenerConfiguration.class)
				.hasSingleBean(AdxScopeRedisListenerConfiguration.class));
	}

}
