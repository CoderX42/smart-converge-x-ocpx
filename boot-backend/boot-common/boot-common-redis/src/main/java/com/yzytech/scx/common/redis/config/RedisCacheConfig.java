

package com.yzytech.scx.common.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * @date 2025/9/18
 */
@Configuration
public class RedisCacheConfig {

	@Bean
	public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
		// 默认配置 + 禁用缓存 null
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().disableCachingNullValues();

		return new RedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory), config);
	}

}
