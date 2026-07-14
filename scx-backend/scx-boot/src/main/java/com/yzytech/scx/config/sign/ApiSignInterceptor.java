/**
 * Copyright (c) 2025 江苏瑶知云科技有限公司
 * All rights reserved.
 */

package com.yzytech.scx.config.sign;

import com.yzytech.scx.common.core.sign.ApiSignConstants;
import com.yzytech.scx.common.core.sign.ApiSignProperties;
import com.yzytech.scx.common.core.sign.SignUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

/**
 * 从请求头读取 X-Timestamp、X-Nonce、X-Sign 做时效与签名校验；可选 Redis 记录 nonce 防重放。
 * 三者均未传时直接放行，兼容旧版小程序（如提审包）在服务端升级后仍可访问。
 */
@Slf4j
@RequiredArgsConstructor
public class ApiSignInterceptor implements HandlerInterceptor {

	private final ApiSignProperties properties;

	private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String timestamp = request.getHeader(ApiSignConstants.HEADER_TIMESTAMP);
		String nonce = request.getHeader(ApiSignConstants.HEADER_NONCE);
		String sign = request.getHeader(ApiSignConstants.HEADER_SIGN);

		if (!SignUtils.checkSign(timestamp, nonce, sign, properties.getSecret(), properties.getExpireMs(),
				properties.getClockSkewMs())) {
			writeUnauthorized(response);
			return false;
		}

		if (properties.isReplayProtection()) {
			StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
			if (redis == null) {
				log.warn("api.sign.replay-protection=true 但未找到 StringRedisTemplate，防重放未生效");
			}
			else {
				String key = SignUtils.nonceRedisKey(nonce);
				Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofMillis(properties.getExpireMs()));
				if (!Boolean.TRUE.equals(first)) {
					writeUnauthorized(response);
					return false;
				}
			}
		}
		return true;
	}

	private static void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"code\":403,\"msg\":\"非法请求\"}");
	}

}
