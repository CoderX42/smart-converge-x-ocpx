package com.yzytech.scx.common.security.listener;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.event.BaseSecurityAuditEvent;
import com.yzytech.scx.common.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes sanitized audit events for authentication and authorization denials. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaseSecurityAuditListener {

	private final ApplicationEventPublisher applicationEventPublisher;

	public void record(HttpServletRequest request, String reasonCode) {
		try {
			String userName = currentUserName();
			applicationEventPublisher
				.publishEvent(new BaseSecurityAuditEvent(this, reasonCode, request.getMethod(), request.getRequestURI(),
						requestParameterNames(request), JakartaServletUtil.getClientIP(request), userName));
		}
		catch (RuntimeException e) {
			// A persistence or listener failure must never turn an authorization denial
			// into a 5xx response.
			log.warn("Unable to publish security-denial audit event reasonCode={}", reasonCode, e);
		}
	}

	private static String currentUserName() {
		try {
			BootUser user = SecurityUtils.getUser();
			return user == null || user.getUsername() == null ? "anonymous" : user.getUsername();
		}
		catch (RuntimeException ignored) {
			return "anonymous";
		}
	}

	private static String requestParameterNames(HttpServletRequest request) {
		return Arrays.stream(request.getParameterMap().keySet().toArray(String[]::new))
			.sorted()
			.collect(java.util.stream.Collectors.joining(",", "params=[", "]"));
	}

}
