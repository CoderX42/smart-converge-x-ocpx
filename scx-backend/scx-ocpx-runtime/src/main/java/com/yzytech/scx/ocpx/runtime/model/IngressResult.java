package com.yzytech.scx.ocpx.runtime.model;

import java.time.OffsetDateTime;

public record IngressResult(String eventId, Status status, OffsetDateTime receivedAt) {

	public enum Status {

		ACCEPTED, DUPLICATE, CONFLICT

	}
}
