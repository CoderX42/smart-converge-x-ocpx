package com.yzytech.scx.tracking.dto;

import lombok.Data;

/**
 * Counters are calculated from the persistent callback queue in the requested media
 * scope.
 */
@Data
public class CallbackQueueStats {

	private Long pendingCount;

	private Long retryingCount;

	private Long pausedCount;

	private Long succeededCount;

	private Long deadCount;

	private Long delayedCount;

	private OutboxQueueStats outbox;

}
