package com.yzytech.scx.adx.config;

import com.yzytech.scx.adx.service.AdxAccessScope;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
public class AdxScopeRedisListenerConfiguration {

	private final RedisMessageListenerContainer listenerContainer;

	private final AdxAccessScope accessScope;

	public AdxScopeRedisListenerConfiguration(
			@Qualifier("redisMessageListenerContainer") RedisMessageListenerContainer listenerContainer,
			AdxAccessScope accessScope) {
		this.listenerContainer = listenerContainer;
		this.accessScope = accessScope;
	}

	@PostConstruct
	void registerScopeInvalidationListener() {
		listenerContainer.addMessageListener(accessScope, ChannelTopic.of(AdxAccessScope.INVALIDATION_TOPIC));
	}

}
