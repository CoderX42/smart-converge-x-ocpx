

package com.yzytech.scx.common.websocket.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;

/**
 * webSocket配置
 *
 * @date 2025/9/1
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketAutoConfiguration {

	@Bean
	public WebSocketConfigurer webSocketConfigurer(List<HandshakeInterceptor> handshakeInterceptor,
			WebSocketHandler webSocketHandler) {
		return registry -> registry.addHandler(webSocketHandler, "/ws")
			.setAllowedOrigins("*")
			.addInterceptors(handshakeInterceptor.toArray(new HandshakeInterceptor[0]));
	}

}
