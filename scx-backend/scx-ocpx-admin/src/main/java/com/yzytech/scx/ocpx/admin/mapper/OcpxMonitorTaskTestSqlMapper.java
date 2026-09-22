package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxMonitorTaskTestSqlMapper {

	java.util.List<SelectResultEventFactRow> selectResultEventFact(@Param("id") String id,
			@Param("advertiserId") String advertiserId, @Param("requestId") String requestId);

	java.util.List<SelectResultFlowLogRow> selectResultFlowLog(@Param("id") String id,
			@Param("advertiserId") String advertiserId, @Param("requestId") String requestId);

	java.util.List<SelectResultEventFact2Row> selectResultEventFact2(@Param("id") String id,
			@Param("advertiserId") String advertiserId, @Param("requestId") String requestId);

	java.util.List<SelectAvailableBalanceAdvertiserAccountRow> selectAvailableBalanceAdvertiserAccount(
			@Param("advertiserId") String advertiserId);

	Long selectFactExistsEventFact(@Param("taskId") String taskId, @Param("requestId") String requestId);

	java.util.List<SelectCallbackFeedbackRow> selectCallbackFeedback(@Param("feedbackLogId") Long feedbackLogId,
			@Param("taskId") String taskId);

	java.util.List<SelectCallbackFeedbackRow> selectLatestCallbackFeedback(@Param("taskId") String taskId);

	record SelectResultEventFactRow(String eventCode, String billingStatus, String billingReasonCode,
			java.math.BigDecimal spendAmount, java.sql.Timestamp receivedAt) {
	}

	record SelectResultFlowLogRow(String resultStatus, Integer partnerHttpStatus, String message, long durationMs,
			java.sql.Timestamp eventTime) {
	}

	record SelectCallbackFeedbackRow(long id, String callbackUrl, String advertiserId, String promotionLinkId, String mediaId,
			String monitorTaskId) {
	}

	record SelectResultEventFact2Row(String eventCode, String billingStatus, String billingReasonCode,
			java.math.BigDecimal spendAmount, java.math.BigDecimal currencyAmount, String deductionDecision,
			java.math.BigDecimal deductionRatioSnapshot, java.sql.Timestamp deductionDecidedAt,
			java.sql.Timestamp receivedAt) {
	}

	record SelectAvailableBalanceAdvertiserAccountRow(java.math.BigDecimal availableBalance) {
	}

	record SelectCallbackFlowRow(String resultStatus, Integer partnerHttpStatus, String message, long durationMs,
			java.sql.Timestamp eventTime) {
	}

	java.util.List<SelectCallbackFlowRow> selectCallbackFlow(@Param("taskId") String taskId,
			@Param("advertiserId") String advertiserId, @Param("requestId") String requestId,
			@Param("downstream") boolean downstream);

}
