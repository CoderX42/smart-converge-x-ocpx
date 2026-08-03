package com.yzytech.scx.ocpx.runtime.model;

public record DeliveryJob(String id, String eventId, String sourceEventId, String direction, String adapterCode,
		String callbackRef, int attemptCount) {
}
