package com.yzytech.scx.ocpx.rta.relay;

public class RelayException extends RuntimeException {

	private final int status;

	public RelayException(int status, String reason) {
		super(reason);
		this.status = status;
	}

	public int status() {
		return status;
	}

}
