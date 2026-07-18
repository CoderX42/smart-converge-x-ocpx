package com.yzytech.scx.tracking.service;

public class CallbackDeliveryException extends RuntimeException {

	private final boolean retryable;

	private final String code;

	public CallbackDeliveryException(boolean retryable, String code, String message) {
		super(message);
		this.retryable = retryable;
		this.code = code;
	}

	public boolean retryable() {
		return retryable;
	}

	public String code() {
		return code;
	}

}
