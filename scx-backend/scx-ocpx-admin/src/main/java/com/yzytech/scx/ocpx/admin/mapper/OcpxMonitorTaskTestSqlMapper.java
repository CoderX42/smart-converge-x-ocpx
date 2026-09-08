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

	record SelectResultEventFactRow(String eventCode, String billingStatus, String billingReasonCode,
			java.math.BigDecimal spendAmount, java.sql.Timestamp receivedAt) {
	}

	record SelectResultFlowLogRow(String resultStatus, Integer partnerHttpStatus, String message, long durationMs,
			java.sql.Timestamp eventTime) {
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
