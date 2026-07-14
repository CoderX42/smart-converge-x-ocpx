package com.yzytech.scx.config.limiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class LimitInterceptor implements HandlerInterceptor {

	private final RateLimiter globalRateLimiter;

	private final LimitProperties limitProperties;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (!limitProperties.isEnabled()) {
			return true;
		}
		String uri = request.getRequestURI();
		for (String excludePath : limitProperties.getExcludePaths()) {
			if (uri.startsWith(excludePath)) {
				return true;
			}
		}
		boolean limited = globalRateLimiter.isLimited(limitProperties.getMaxQps(), limitProperties.getWindowSeconds());
		if (limited) {
			response.setStatus(429);
			response.setContentType("application/json;charset=utf-8");
			response.getWriter().write("{\"code\":429,\"msg\":\"系统繁忙，请稍后再试\"}");
			return false;
		}
		return true;
	}

}
