package com.yzytech.scx.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Keeps one request identifier visible to clients and server logs. The header is
 * intentionally accepted from trusted gateways so a complete request chain can
 * be correlated without putting identity or credentials in log fields.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	private static final String HEADER = "X-Request-Id";
	private static final String MDC_KEY = "requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String requestId = request.getHeader(HEADER);
		if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
			requestId = UUID.randomUUID().toString();
		}
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}
}
