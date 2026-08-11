package com.yzytech.scx.ocpx.admin.config;

import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
public class OcpxScopeRedisListenerConfiguration {

	private final RedisMessageListenerContainer listenerContainer;

	private final OcpxAccessScope accessScope;

	public OcpxScopeRedisListenerConfiguration(
			@Qualifier("redisMessageListenerContainer") RedisMessageListenerContainer listenerContainer,
			OcpxAccessScope accessScope) {
		this.listenerContainer = listenerContainer;
		this.accessScope = accessScope;
	}

	@PostConstruct
	void registerScopeInvalidationListener() {
		listenerContainer.addMessageListener(accessScope, ChannelTopic.of(OcpxAccessScope.INVALIDATION_TOPIC));
	}

}
