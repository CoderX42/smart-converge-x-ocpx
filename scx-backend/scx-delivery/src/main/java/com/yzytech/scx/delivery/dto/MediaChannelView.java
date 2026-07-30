package com.yzytech.scx.delivery.dto;

import com.yzytech.scx.delivery.entity.MediaChannel;
import java.time.LocalDateTime;

public record MediaChannelView(String id, String mediaId, String code, String name, String documentId,
		String description, String status, Integer version, long documentCount, long channelUserCount,
		LocalDateTime createTime, LocalDateTime updateTime) {

	public static MediaChannelView from(MediaChannel value, long channelUserCount) {
		return new MediaChannelView(value.getId(), value.getMediaId(), value.getCode(), value.getName(),
				value.getDocumentId(), value.getDescription(), value.getStatus(), value.getVersion(),
				value.getDocumentId() == null ? 0 : 1, channelUserCount, value.getCreateTime(), value.getUpdateTime());
	}
}
