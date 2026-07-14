

package com.yzytech.scx.pay.api.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @date 2025/7/7
 */
public class TransactionalMqUtils {

	public static void sendAfterCommit(Runnable sendLogic) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					sendLogic.run();
				}
			});
		}
		else {
			// 无事务，立即执行
			sendLogic.run();
		}
	}

}
