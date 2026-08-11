package com.yzytech.scx.ocpx.admin.config;

import com.yzytech.scx.ocpx.admin.service.OcpxRouteDistributionApplyWorker;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "scx.ocpx.admin.tracking", name = "distribution-worker-enabled", havingValue = "true")
public class OcpxRouteDistributionRedisListenerConfiguration {

	private final RedisMessageListenerContainer listenerContainer;

	private final OcpxRouteDistributionApplyWorker applyWorker;

	private final OcpxTrackingProperties properties;

	public OcpxRouteDistributionRedisListenerConfiguration(
			@Qualifier("redisMessageListenerContainer") RedisMessageListenerContainer listenerContainer,
			OcpxRouteDistributionApplyWorker applyWorker, OcpxTrackingProperties properties) {
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
