package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.core.OcpxRefreshContract;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OcpxConfigRefreshPublisher {

	private final StringRedisTemplate redis;

	public OcpxConfigRefreshPublisher(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void mark() {
		redis.opsForValue().set(OcpxRefreshContract.REDIS_KEY, "1");
	}

}
