

package com.yzytech.scx.common.websocket.handler;

import cn.dev33.satoken.exception.SaTokenException;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Objects;

@Slf4j
public class BootHandshakeInterceptor implements HandshakeInterceptor {

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) throws Exception {
		if (!attributes.containsKey(CommonConstants.TOKEN_KEY) && request instanceof ServletServerHttpRequest) {
			HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
			String token = servletRequest.getParameter(CommonConstants.TOKEN_KEY);
			if (StringUtils.hasText(token)) {
				BootUser hxUser;
				try {
					hxUser = SecurityUtils.getUser(token);
				}
				catch (SaTokenException ex) {
					log.warn("WebSocket 握手拒绝：token 已失效");
					response.setStatusCode(HttpStatus.UNAUTHORIZED);
					return false;
				}
				if (Objects.nonNull(hxUser)) {
					attributes.put("userId", hxUser.getUserId());
				}
			}
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {

	}

}
