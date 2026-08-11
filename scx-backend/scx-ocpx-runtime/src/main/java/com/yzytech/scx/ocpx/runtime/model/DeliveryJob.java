package com.yzytech.scx.ocpx.runtime.model;

public record DeliveryJob(String id, String eventId, String sourceEventId, String direction, String adapterCode,
		String callbackRef, int attemptCount, String debugExecutionId, String leaseToken) {

	public DeliveryJob(String id, String eventId, String sourceEventId, String direction, String adapterCode,
			String callbackRef, int attemptCount, String debugExecutionId) {
		this(id, eventId, sourceEventId, direction, adapterCode, callbackRef, attemptCount, debugExecutionId, null);
	}

	public DeliveryJob(String id, String eventId, String sourceEventId, String direction, String adapterCode,
			String callbackRef, int attemptCount) {
		this(id, eventId, sourceEventId, direction, adapterCode, callbackRef, attemptCount, null, null);
	}

	public DeliveryJob claimedWith(String token) {
		return new DeliveryJob(id, eventId, sourceEventId, direction, adapterCode, callbackRef, attemptCount,
				debugExecutionId, token);
	}
}
