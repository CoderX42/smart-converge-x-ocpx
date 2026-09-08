package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxMonitorTaskIntegrationSqlMapper {

	java.util.List<SelectFeedbackLogFeedbackRow> selectFeedbackLogFeedback(@Param("feedbackLogId") Long feedbackLogId,
			@Param("taskId") String taskId);

	int insertWriteAuditFlowLog(@Param("command") InsertWriteAuditFlowLogCommand command);

	record SelectFeedbackLogFeedbackRow(String reqId, String callbackUrl, String aId, String lId, String mId,
			String tId) {
	}

	record Query(String taskId, java.time.LocalDateTime start, java.time.LocalDateTime end, String requestId,
			long limit, long offset) {
	}

	Long countFeedback(@Param("q") Query q);

	record SelectFeedbackRow(String callbackUrl, long id, java.sql.Timestamp createTime, String reqId, String clkId,
			String userid, String userType, String deviceOs, String deviceDid, String deviceOaid, String deviceDpid,
			String deviceMac, String deviceIdfa, String deviceCaid, String deviceCaid1, String ip, String ua, String ts,
			String tsMs, String advId, String groupId, String planId, String creativeId, String mId, String aId,
			String tId, String lId) {
	}

	java.util.List<SelectFeedbackRow> selectFeedback(@Param("q") Query q);

	record InsertWriteAuditFlowLogCommand(String id, String flowType, String eventId, String sourceEventId,
			String eventCode, String payloadHash, String advertiserId, String promotionLinkId, String mediaId,
			String monitorTaskId, String resultStatus, Integer partnerHttpStatus, String message, long durationMs,
			java.time.LocalDateTime eventTime) {
	}

}
