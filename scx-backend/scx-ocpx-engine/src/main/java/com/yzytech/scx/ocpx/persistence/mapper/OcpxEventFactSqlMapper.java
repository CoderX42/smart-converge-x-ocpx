package com.yzytech.scx.ocpx.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxEventFactSqlMapper {

	int updateRecordFeedbackAdvertiserAccount(@Param("balanceAfter") java.math.BigDecimal balanceAfter,
			@Param("advertiserId") String advertiserId);

	int updateRecordCallbackAdvertiserAccount(@Param("balanceAfter") java.math.BigDecimal balanceAfter,
			@Param("advertiserId") String advertiserId);

	int updateFinalizeDeductionEventFact(@Param("name") String name,
			@Param("normalizedRatio") java.math.BigDecimal normalizedRatio,
			@Param("decidedAt") java.time.LocalDateTime decidedAt, @Param("factId") String factId);

	int insertFinalizeDeductionHourlyMetric(@Param("passed") long passed, @Param("deducted") long deducted,
			@Param("factId") String factId);

	java.util.List<SelectExistingCallbackEventFactRow> selectExistingCallbackEventFact(
			@Param("factKey") String factKey);

	int insertInsertFactEventFact(@Param("command") InsertInsertFactEventFactCommand command);

	java.util.List<SelectRequireTaskMonitorTaskRow> selectRequireTaskMonitorTask(@Param("taskId") String taskId,
			@Param("advertiserId") String advertiserId, @Param("linkId") String linkId,
			@Param("mediaId") String mediaId);

	java.util.List<SelectLockBalanceAdvertiserAccountRow> selectLockBalanceAdvertiserAccount(
			@Param("advertiserId") String advertiserId);

	int insertInsertChargeEntryBillingEntry(@Param("command") InsertInsertChargeEntryBillingEntryCommand command);

	int updateUpdateFactBillingEventFact(@Param("spendAmount") java.math.BigDecimal spendAmount,
			@Param("billingStatus") String billingStatus, @Param("reasonCode") String reasonCode,
			@Param("factId") String factId);

	int insertUpsertMetricHourlyMetric(@Param("command") InsertUpsertMetricHourlyMetricCommand command);

	record SelectExistingCallbackEventFactRow(boolean targetConversion, String id, String deductionDecision) {
	}

	record SelectRequireTaskMonitorTaskRow(String id, String advertiserId, String promotionLinkId, String mediaId,
			boolean billingEnabled, int billingType, java.math.BigDecimal unitPrice, String conversionEvent) {
	}

	record SelectLockBalanceAdvertiserAccountRow(java.math.BigDecimal availableBalance) {
	}

	record InsertInsertFactEventFactCommand(String id, String factKey, String flowType, String eventCode,
			String requestId, String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId,
			boolean targetConversion, boolean billingEnabledSnapshot, Integer billingTypeSnapshot,
			java.math.BigDecimal billingUnitPriceSnapshot, String conversionEventSnapshot, String deductionDecision,
			java.math.BigDecimal spendAmount, java.math.BigDecimal currencyAmount, String billingStatus,
			String billingReasonCode, java.time.LocalDateTime receivedAt) {
	}

	record InsertInsertChargeEntryBillingEntryCommand(String id, String advertiserId, String monitorTaskId,
			String eventFactId, String entryType, String status, java.math.BigDecimal amount,
			java.math.BigDecimal balanceBefore, java.math.BigDecimal balanceAfter, String businessKey,
			String reasonCode, java.time.LocalDateTime occurredAt) {
	}

	record InsertUpsertMetricHourlyMetricCommand(int timeHour, String advertiserId, String promotionLinkId,
			String mediaId, String monitorTaskId, long impressionCount, long clickCount, long targetConversionCount,
			long callbackCount, long activateCount, long evokeCount, long loginCount, long registerCount,
			long paymentCount, long customerAcquisitionCount, long nextStayCount, long visitsCount,
			long authenticationCount, long placeOrderCount, long finishTheOrderCount, long firstActivationCount,
			long firstOrderCount, long downloadCount, long activityCount, long addictionCount,
			java.math.BigDecimal spendAmount, java.math.BigDecimal currencyAmount, long rejectedClickCount,
			long rejectedConversionCount) {
	}

}
