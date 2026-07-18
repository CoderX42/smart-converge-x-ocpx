package com.yzytech.scx.tracking.service;

public class RtaTransportException extends RuntimeException {

	private final String code;

	public RtaTransportException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}

}
