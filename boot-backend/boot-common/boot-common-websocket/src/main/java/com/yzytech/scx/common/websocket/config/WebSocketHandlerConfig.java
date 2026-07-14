

package com.yzytech.scx.common.websocket.config;

import com.yzytech.scx.common.websocket.handler.BootHandshakeInterceptor;
import com.yzytech.scx.common.websocket.handler.BootWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;

@Configuration
public class WebSocketHandlerConfig {

	@Bean
	public WebSocketHandler webSocketHandler() {
		return new BootWebSocketHandler();
	}

	@Bean
	public BootHandshakeInterceptor hxHandshakeInterceptor() {
		return new BootHandshakeInterceptor();
	}

}
