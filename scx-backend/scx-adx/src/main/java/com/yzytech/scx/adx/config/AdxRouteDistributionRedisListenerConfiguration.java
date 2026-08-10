package com.yzytech.scx.adx.config;

import com.yzytech.scx.adx.service.AdxRouteDistributionApplyWorker;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "scx.adx.tracking", name = "distribution-worker-enabled", havingValue = "true")
public class AdxRouteDistributionRedisListenerConfiguration {

	private final RedisMessageListenerContainer listenerContainer;

	private final AdxRouteDistributionApplyWorker applyWorker;

	private final AdxTrackingProperties properties;

	public AdxRouteDistributionRedisListenerConfiguration(
			@Qualifier("redisMessageListenerContainer") RedisMessageListenerContainer listenerContainer,
			AdxRouteDistributionApplyWorker applyWorker, AdxTrackingProperties properties) {
		this.listenerContainer = listenerContainer;
		this.applyWorker = applyWorker;
		this.properties = properties;
	}

	@PostConstruct
	void registerRouteDistributionListener() {
		if (properties.isDistributionWorkerEnabled()) {
			listenerContainer.addMessageListener(applyWorker,
					ChannelTopic.of(RouteConfigurationCache.INVALIDATION_TOPIC));
		}
	}

}
