package com.yzytech.scx.tracking.service;

public interface CallbackTransport {

	void post(String endpoint, String idempotencyKey, String payload);

}
