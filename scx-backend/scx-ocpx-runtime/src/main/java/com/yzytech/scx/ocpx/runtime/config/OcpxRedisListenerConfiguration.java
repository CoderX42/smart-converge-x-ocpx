package com.yzytech.scx.ocpx.runtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class OcpxRedisListenerConfiguration {

	@Bean
	RedisMessageListenerContainer ocpxRedisMessageListenerContainer(RedisConnectionFactory connectionFactory,
			RouteConfigurationCache routeConfigurationCache) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(routeConfigurationCache,
				ChannelTopic.of(RouteConfigurationCache.INVALIDATION_TOPIC));
		return container;
	}

}
