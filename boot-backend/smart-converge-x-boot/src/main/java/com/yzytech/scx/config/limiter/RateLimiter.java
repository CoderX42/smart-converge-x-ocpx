package com.yzytech.scx.config.limiter;

import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Component
public class RateLimiter {

	private final StringRedisTemplate redisTemplate;

	private final DefaultRedisScript<Long> script;

	// 全局限流 KEY
	private static final String GLOBAL_KEY = "app:global:rate:limit";

	public RateLimiter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
		script = new DefaultRedisScript<>("""
				local count = redis.call("INCR", KEYS[1])
				if count == 1 then
				    redis.call("EXPIRE", KEYS[1], ARGV[2])
				end
				return count > tonumber(ARGV[1]) and 1 or 0
				""", Long.class);
	}

	/**
	 * 全局限流：整个应用共用一套限流
	 * @param maxQPS 最大QPS
	 * @param window 窗口秒
	 */
	public boolean isLimited(int maxQPS, int window) {
		Long res = redisTemplate.execute(script, List.of(GLOBAL_KEY), String.valueOf(maxQPS), String.valueOf(window));
		return res != null && res == 1;
	}

}
