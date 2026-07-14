

package com.yzytech.scx.common.websocket.utils;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class SessionUtils {

	// userId -> session
	private static final ConcurrentHashMap<String, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

	// sessionId -> userId (便于反查和清理)
	private static final ConcurrentHashMap<String, String> SESSION_USER_MAP = new ConcurrentHashMap<>();

	/**
	 * 新增 session
	 */
	public static void addSession(WebSocketSession session) {
		String userId = (String) session.getAttributes().get("userId");
		if (userId == null) {
			return;
		}
		SESSION_MAP.put(userId, session);
		SESSION_USER_MAP.put(session.getId(), userId);
	}

	/**
	 * 根据 userId 获取 session
	 */
	public static WebSocketSession getSession(String userId) {
		return SESSION_MAP.get(userId);
	}

	/**
	 * 删除 session (根据 userId)
	 */
	public static void removeSession(String userId) {
		WebSocketSession session = SESSION_MAP.remove(userId);
		if (session != null) {
			SESSION_USER_MAP.remove(session.getId());
			try {
				if (session.isOpen()) {
					session.close(CloseStatus.NORMAL);
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 删除 session (根据 sessionId)
	 */
	public static void removeSessionBySessionId(String sessionId) {
		String userId = SESSION_USER_MAP.remove(sessionId);
		if (userId != null) {
			removeSession(userId);
		}
	}

	/**
	 * 获取在线用户数
	 */
	public static int getOnlineCount() {
		return SESSION_MAP.size();
	}

	/**
	 * 清理所有 session
	 */
	public static void clearAll() {
		SESSION_MAP.forEach((userId, session) -> {
			try {
				if (session.isOpen()) {
					session.close(CloseStatus.NORMAL);
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		});
		SESSION_MAP.clear();
		SESSION_USER_MAP.clear();
	}

	/**
	 * 获取所有在线 session
	 */
	public static Collection<WebSocketSession> getAllSessions() {
		return SESSION_MAP.values();
	}

}
