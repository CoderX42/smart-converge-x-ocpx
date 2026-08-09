package com.yzytech.scx.ocpx.runtime.model;

public record DeliveryJob(String id, String eventId, String sourceEventId, String direction, String adapterCode,
		String callbackRef, int attemptCount, String debugExecutionId) {

	public DeliveryJob(String id, String eventId, String sourceEventId, String direction, String adapterCode,
			String callbackRef, int attemptCount) {
		this(id, eventId, sourceEventId, direction, adapterCode, callbackRef, attemptCount, null);
	}
}
