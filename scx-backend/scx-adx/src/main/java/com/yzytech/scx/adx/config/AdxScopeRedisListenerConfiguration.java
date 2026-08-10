package com.yzytech.scx.adx.config;

import com.yzytech.scx.adx.service.AdxAccessScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class AdxScopeRedisListenerConfiguration {

	private final RedisMessageListenerContainer listenerContainer;

	private final AdxAccessScope accessScope;

	@PostConstruct
	void registerScopeInvalidationListener() {
		listenerContainer.addMessageListener(accessScope, ChannelTopic.of(AdxAccessScope.INVALIDATION_TOPIC));
	}

}
