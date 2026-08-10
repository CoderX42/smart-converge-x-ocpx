package com.yzytech.scx.adx.config;

import com.yzytech.scx.adx.service.AdxRouteDistributionApplyWorker;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class AdxRouteDistributionRedisListenerConfiguration {

	private final RedisMessageListenerContainer listenerContainer;

	private final AdxRouteDistributionApplyWorker applyWorker;

	private final AdxTrackingProperties properties;

	@PostConstruct
	void registerRouteDistributionListener() {
		if (properties.isDistributionWorkerEnabled()) {
			listenerContainer.addMessageListener(applyWorker,
					ChannelTopic.of(RouteConfigurationCache.INVALIDATION_TOPIC));
		}
	}

}
