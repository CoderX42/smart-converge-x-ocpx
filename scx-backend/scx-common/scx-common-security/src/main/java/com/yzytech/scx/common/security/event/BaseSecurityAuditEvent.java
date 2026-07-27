package com.yzytech.scx.common.security.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * A sanitized security-denial audit record. Request values are deliberately not retained
 * so credentials and identifiers cannot enter the audit pipeline.
 */
@Getter
public class BaseSecurityAuditEvent extends ApplicationEvent {

	private final String reasonCode;

	private final String requestMethod;

	private final String requestUri;

	private final String requestParams;

	private final String ipAddr;

	private final String userName;

	public BaseSecurityAuditEvent(Object source, String reasonCode, String requestMethod, String requestUri,
			String requestParams, String ipAddr, String userName) {
		super(source);
		this.reasonCode = reasonCode;
		this.requestMethod = requestMethod;
		this.requestUri = requestUri;
		this.requestParams = requestParams;
		this.ipAddr = ipAddr;
		this.userName = userName;
	}

}
