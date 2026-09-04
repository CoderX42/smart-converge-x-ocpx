package com.yzytech.scx.ocpx.rta.exception;

/** Redis、Stream 等关键依赖不可用时的 Fail-closed 异常。 */
public class RtaInfrastructureException extends RuntimeException {

	public RtaInfrastructureException(String message) {
		super(message);
	}

	public RtaInfrastructureException(String message, Throwable cause) {
		super(message, cause);
	}

}
