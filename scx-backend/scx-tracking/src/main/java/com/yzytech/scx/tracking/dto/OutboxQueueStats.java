package com.yzytech.scx.tracking.dto;

import lombok.Data;

/**
 * Counters are calculated from persistent domain-event outbox records in media/task
 * scope.
 */
@Data
public class OutboxQueueStats {

	private Long pendingCount;

	private Long retryingCount;

	private Long sentCount;

	private Long deadCount;

	private Long delayedCount;

}
