package com.yzytech.scx.tracking.service;

public record PostbackRequest(String endpoint, String idempotencyKey, String payload, String clientId,
		String keyVersion, String secret) {
}
