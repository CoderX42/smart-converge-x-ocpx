package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.core.OcpxRefreshContract;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class OcpxConfigRefreshPublisher {

	private final StringRedisTemplate redis;

	public OcpxConfigRefreshPublisher(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void mark() {
		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					write();
				}
			});
			return;
		}
		write();
	}

	private void write() {
		redis.opsForValue().set(OcpxRefreshContract.REDIS_KEY, "1");
	}

}
