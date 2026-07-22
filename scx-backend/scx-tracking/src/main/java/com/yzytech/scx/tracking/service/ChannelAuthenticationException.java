package com.yzytech.scx.tracking.service;

public class ChannelAuthenticationException extends RuntimeException {

	private final int status;

	public ChannelAuthenticationException(int status, String message) {
		super(message);
		this.status = status;
	}

	public int status() {
		return status;
	}

}
