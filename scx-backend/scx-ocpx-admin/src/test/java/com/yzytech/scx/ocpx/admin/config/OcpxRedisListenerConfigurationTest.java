package com.yzytech.scx.ocpx.admin.config;

import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope;
import com.yzytech.scx.ocpx.admin.service.OcpxRouteDistributionApplyWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OcpxRedisListenerConfigurationTest {

	@Test
	void usesTheManagementListenerContainerWhenOcpxContainerIsAlsoPresent() {
		RedisMessageListenerContainer managementContainer = mock(RedisMessageListenerContainer.class);
		RedisMessageListenerContainer ocpxContainer = mock(RedisMessageListenerContainer.class);

		new ApplicationContextRunner()
			.withPropertyValues("scx.ocpx.admin.tracking.distribution-worker-enabled=true")
			.withBean("redisMessageListenerContainer", RedisMessageListenerContainer.class, () -> managementContainer)
			.withBean("ocpxRedisMessageListenerContainer", RedisMessageListenerContainer.class, () -> ocpxContainer)
			.withBean(OcpxRouteDistributionApplyWorker.class, () -> mock(OcpxRouteDistributionApplyWorker.class))
			.withBean(OcpxTrackingProperties.class, () -> mock(OcpxTrackingProperties.class))
			.withBean(OcpxAccessScope.class, () -> mock(OcpxAccessScope.class))
			.withUserConfiguration(OcpxRouteDistributionRedisListenerConfiguration.class,
					OcpxScopeRedisListenerConfiguration.class)
			.run(context -> assertThat(context).hasNotFailed()
				.hasSingleBean(OcpxRouteDistributionRedisListenerConfiguration.class)
				.hasSingleBean(OcpxScopeRedisListenerConfiguration.class));
	}

}
