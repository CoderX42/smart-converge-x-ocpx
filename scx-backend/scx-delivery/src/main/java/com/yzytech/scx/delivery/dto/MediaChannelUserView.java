package com.yzytech.scx.delivery.dto;

import com.yzytech.scx.delivery.entity.MediaChannelUser;
import java.time.LocalDateTime;

/**
 * Safe list representation. Phone and email values are masked before leaving the
 * service layer so a future controller cannot accidentally expose raw contacts.
 */
public record MediaChannelUserView(String id, String mediaId, String channelId, String account, String name,
		String phone, String email, String status, Integer version, LocalDateTime createTime,
		LocalDateTime updateTime) {

	public static MediaChannelUserView masked(MediaChannelUser value) {
		return new MediaChannelUserView(value.getId(), value.getMediaId(), value.getChannelId(), value.getAccount(),
				value.getName(), maskPhone(value.getPhone()), maskEmail(value.getEmail()), value.getStatus(),
				value.getVersion(), value.getCreateTime(), value.getUpdateTime());
	}

	public static MediaChannelUserView sensitive(MediaChannelUser value) {
		return new MediaChannelUserView(value.getId(), value.getMediaId(), value.getChannelId(), value.getAccount(),
				value.getName(), value.getPhone(), value.getEmail(), value.getStatus(), value.getVersion(),
				value.getCreateTime(), value.getUpdateTime());
	}

	static String maskPhone(String value) {
		if (value == null || value.isBlank())
			return value;
		String normalized = value.trim();
		if (normalized.length() <= 4)
			return "*".repeat(normalized.length());
		if (normalized.length() < 7)
			return normalized.charAt(0) + "*".repeat(normalized.length() - 2)
					+ normalized.charAt(normalized.length() - 1);
		return normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4);
	}

	static String maskEmail(String value) {
		if (value == null || value.isBlank())
			return value;
		String normalized = value.trim();
		int at = normalized.indexOf('@');
		if (at <= 0 || at == normalized.length() - 1)
			return "***";
		return normalized.substring(0, 1) + "***" + normalized.substring(at);
	}
}
