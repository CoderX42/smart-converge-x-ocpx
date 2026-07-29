package com.yzytech.scx.delivery.dto;

import com.yzytech.scx.delivery.entity.IntegrationDocument;

import java.time.LocalDateTime;

/**
 * Safe document projection for management clients. Internal storage identifiers and
 * object keys deliberately stay server-side.
 */
public record IntegrationDocumentView(String id, String mediaId, String name, String description, String type,
		Integer documentVersion, String storageType, String externalUrl, String originalFileName, String mimeType,
		Long fileSize, String status, Integer version, String createBy, String updateBy, LocalDateTime createTime,
		LocalDateTime updateTime) {

	public static IntegrationDocumentView from(IntegrationDocument source) {
		return new IntegrationDocumentView(source.getId(), source.getMediaId(), source.getName(),
				source.getDescription(), source.getType(), source.getDocumentVersion(), source.getStorageType(),
				source.getExternalUrl(), source.getOriginalFileName(), source.getMimeType(), source.getFileSize(),
				source.getStatus(), source.getVersion(), source.getCreateBy(), source.getUpdateBy(),
				source.getCreateTime(), source.getUpdateTime());
	}

}
