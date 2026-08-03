package com.yzytech.scx.ocpx.runtime.error;

import org.springframework.http.HttpStatus;

public class OcpxException extends RuntimeException {

	private final HttpStatus status;

	public OcpxException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

}
