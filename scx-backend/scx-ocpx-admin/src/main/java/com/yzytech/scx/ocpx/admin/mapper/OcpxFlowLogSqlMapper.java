package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxFlowLogSqlMapper {

	java.util.List<com.yzytech.scx.ocpx.admin.dto.FlowLogViews.Option> selectAdvertiserOptions(
			@Param("advertiserId") String advertiserId);

	java.util.List<com.yzytech.scx.ocpx.admin.dto.FlowLogViews.Option> selectLinkOptions(
			@Param("advertiserId") String advertiserId);

	java.util.List<com.yzytech.scx.ocpx.admin.dto.FlowLogViews.Option> selectMediaOptions(
			@Param("advertiserId") String advertiserId);

	java.util.List<com.yzytech.scx.ocpx.admin.dto.FlowLogViews.Option> selectTaskOptions(
			@Param("advertiserId") String advertiserId, @Param("mediaId") String mediaId);

	record Query(java.time.LocalDateTime start, java.time.LocalDateTime end, String flowType, String status,
			String advertiserId, String linkId, String mediaId, String taskId, String eventId, String stage,
			boolean timeline, long limit, long offset) {
	}

	record SelectPageRow(String id, String flowType, String traceId, String stageCode, String eventId,
			String sourceEventId, String eventCode, String advertiserId, String advertiserName, String promotionLinkId,
			String linkName, String mediaId, String mediaName, String monitorTaskId, String monitorName,
			String resultStatus, Integer partnerHttpStatus, String message, long durationMs,
			java.sql.Timestamp eventTime, java.sql.Timestamp createTime) {
	}

	java.util.List<SelectPageRow> selectPage(@Param("q") Query q);

	record SelectDetailRow(String flowLogId, String flowType, String traceId, String eventId, String sourceEventId,
			String eventCode, String resultStatus, Integer partnerHttpStatus, String message, String responsePreview,
			long durationMs, java.sql.Timestamp eventTime, java.sql.Timestamp flowCreatedAt, Long feedbackLogId,
			java.sql.Timestamp feedbackReceivedAt, String reqId, String clkId, String impId, String userid,
			String userType, String deviceOs, String osv, String model, String deviceDid, String deviceImeiMd5,
			String deviceOaid, String deviceOaidMd5, String deviceDpid, String deviceAndroididMd5, String deviceIdfa,
			String deviceIdfaMd5, String aaid, String deviceCaid, String deviceCaid1, String deviceCaid2,
			String deviceCaidList, String lastCaid, String prevCaid, String deviceMac, String ip, String ipv6,
			String ua, String callbackUrl, String ts, String tsMs, String advId, String groupId, String planId,
			String creativeId, String mId, String aId, String tId, String lId) {
	}

	java.util.List<SelectDetailRow> selectDetail(@Param("id") String id);

	record SelectStagesRow(String id, String stageCode, String flowType, String eventCode, String resultStatus,
			String requestMethod, String requestEndpoint, Integer partnerHttpStatus, long durationMs, String message,
			String requestParams, String macroAudit, String responsePreview, boolean requestTruncated,
			java.sql.Timestamp eventTime) {
	}

	java.util.List<SelectStagesRow> selectStages(@Param("traceId") String traceId, @Param("id") String id);

}
