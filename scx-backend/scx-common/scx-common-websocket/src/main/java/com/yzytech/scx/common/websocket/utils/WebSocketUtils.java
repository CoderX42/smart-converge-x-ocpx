

package com.yzytech.scx.common.websocket.utils;

import cn.hutool.json.JSONUtil;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.List;

/**
 * 工具类
 *
 * @date 2025/9/1
 */

public class WebSocketUtils {

	/**
	 * 给指定用户发消息
	 */
	public static void sendToUser(String userId, Object message) {

		WebSocketSession session = SessionUtils.getSession(userId);
		if (session != null && session.isOpen()) {
			try {

				session.sendMessage(new TextMessage(JSONUtil.toJsonStr(message)));
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void sendToUsers(List<String> userIds, Object message) {
		if (CollectionUtils.isEmpty(userIds)) {
			return;
		}
		for (String userId : userIds) {
			WebSocketSession session = SessionUtils.getSession(userId);
			if (session != null && session.isOpen()) {
				try {
					session.sendMessage(new TextMessage(JSONUtil.toJsonStr(message)));
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void sendToAll(Object message) {
		Collection<WebSocketSession> sessions = SessionUtils.getAllSessions(); // 需要你在
																				// SessionUtils
																				// 里加个方法
		if (!CollectionUtils.isEmpty(sessions)) {
			for (WebSocketSession session : sessions) {
				if (session != null && session.isOpen()) {
					try {
						session.sendMessage(new TextMessage(JSONUtil.toJsonStr(message)));
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

}
