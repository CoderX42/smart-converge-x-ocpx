package com.yzytech.scx.ocpx.rta.exception;

/** 同一请求 ID 携带不同内容时拒绝覆盖已有决定。 */
public class RtaConflictException extends RuntimeException {

	public RtaConflictException(String message) {
		super(message);
	}

}
