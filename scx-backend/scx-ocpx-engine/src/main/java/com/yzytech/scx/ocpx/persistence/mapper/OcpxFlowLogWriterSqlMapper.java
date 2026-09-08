package com.yzytech.scx.ocpx.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxFlowLogWriterSqlMapper {

	int insertWriteFlowLog(@Param("command") InsertWriteFlowLogCommand command);

	int insertWriteDetailFlowLogDetail(@Param("command") InsertWriteDetailFlowLogDetailCommand command);

	record InsertWriteFlowLogCommand(String id, String traceId, String flowType, String stageCode, String eventId,
			String sourceEventId, String eventCode, String requestMethod, String requestEndpoint, String macroAudit,
			String payloadHash, String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId,
			String resultStatus, Integer partnerHttpStatus, String message, String responsePreview, long durationMs,
			java.time.LocalDateTime eventTime) {
	}

	record InsertWriteDetailFlowLogDetailCommand(String id, String flowLogId, String requestParams,
			String responsePreview, boolean requestTruncated, java.time.LocalDateTime expiresAt) {
	}

}
