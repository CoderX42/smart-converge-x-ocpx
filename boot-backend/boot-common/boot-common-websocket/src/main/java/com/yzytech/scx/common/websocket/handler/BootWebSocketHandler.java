

package com.yzytech.scx.common.websocket.handler;

import com.yzytech.scx.common.websocket.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 通用 WebSocket 处理器
 *
 * @date 2025/9/1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootWebSocketHandler extends TextWebSocketHandler {

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		SessionUtils.addSession(session);
		log.info("WebSocket 连接成功: sessionId=" + session.getId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		String payload = message.getPayload();
		if (!StringUtils.hasText(payload)) {
			return; // 空消息直接忽略
		}
		// 心跳
		if (payload.equals("ping")) {
			session.sendMessage(new TextMessage("pong"));
			return;
		}
		// 原文返回
		session.sendMessage(new TextMessage(payload));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		SessionUtils.removeSessionBySessionId(session.getId());
		log.info("WebSocket 连接关闭: sessionId=" + session.getId());
	}

}
