package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.core.OcpxRefreshContract;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class OcpxConfigRefreshPublisherTest {

	@Test
	@SuppressWarnings("unchecked")
	void writesTheSharedLegacyRefreshFlag() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);

		new OcpxConfigRefreshPublisher(redis).mark();

		verify(values).set(OcpxRefreshContract.REDIS_KEY, "1");
	}

	@Test
	@SuppressWarnings("unchecked")
	void transactionPublishesOnlyAfterCommit() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		TransactionSynchronizationManager.setActualTransactionActive(true);
		TransactionSynchronizationManager.initSynchronization();
		try {
			new OcpxConfigRefreshPublisher(redis).mark();
			verify(values, never()).set(OcpxRefreshContract.REDIS_KEY, "1");

			TransactionSynchronizationManager.getSynchronizations()
				.forEach(TransactionSynchronization::afterCommit);

			verify(values).set(OcpxRefreshContract.REDIS_KEY, "1");
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void rollbackDoesNotPublishRefresh() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		TransactionSynchronizationManager.setActualTransactionActive(true);
		TransactionSynchronizationManager.initSynchronization();
		try {
			new OcpxConfigRefreshPublisher(redis).mark();
			TransactionSynchronizationManager.getSynchronizations()
				.forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

			verify(values, never()).set(OcpxRefreshContract.REDIS_KEY, "1");
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

}
