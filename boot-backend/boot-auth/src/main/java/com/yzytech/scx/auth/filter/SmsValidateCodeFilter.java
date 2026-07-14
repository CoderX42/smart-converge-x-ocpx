

package com.yzytech.scx.auth.filter;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.constant.CacheConstants;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.constant.SecurityConstants;
import com.yzytech.scx.common.core.util.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsValidateCodeFilter extends OncePerRequestFilter {

	private final StringRedisTemplate redisTemplate;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// 验证码校验逻辑
		if (!StrUtil.containsAnyIgnoreCase(request.getServletPath(), SecurityConstants.PHONE_SMS_TOKEN,
				SecurityConstants.TOC_PHONE_SMS_TOKEN)) {
			filterChain.doFilter(request, response);
			return;
		}
		String code = request.getParameter("code");
		String phone = request.getParameter("phone");

		if (!StringUtils.hasText(code)) {
			writeErrorResponse(response, "验证码为空", CommonConstants.FAIL);
			return;
		}

		String redisCode = redisTemplate.opsForValue().get(CacheConstants.SMS_CODE_KEY + phone);
		if (!StringUtils.hasText(redisCode) || !code.equals(redisCode)) {
			writeErrorResponse(response, "验证码不合法", CommonConstants.FAIL);
			return;
		}

		redisTemplate.delete(CacheConstants.SMS_CODE_KEY + phone);
		filterChain.doFilter(request, response);
	}

	private void writeErrorResponse(HttpServletResponse response, String msg, int status) throws IOException {
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
		Result result = Result.fail(status, msg); // 你自己的 Result 返回体
		ObjectMapper objectMapper = new ObjectMapper();
		response.getWriter().write(objectMapper.writeValueAsString(result));
	}

}
