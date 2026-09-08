package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxCallbackRecordSqlMapper {

	record Query(java.time.LocalDateTime start, java.time.LocalDateTime end, String advertiserId, String linkId,
			String mediaId, String taskId, String eventCode, String decision, String requestId,
			Boolean targetConversion, long limit, long offset) {
	}

	Long count(@Param("q") Query q);

	record SelectPageRow(String id, String requestId, String eventCode, boolean targetConversion, String advertiserId,
			String advertiserName, String promotionLinkId, String linkName, String mediaId, String mediaName,
			String monitorTaskId, String monitorName, String billingStatus, String billingReasonCode,
			java.math.BigDecimal spendAmount, java.math.BigDecimal currencyAmount, String deductionDecision,
			java.math.BigDecimal deductionRatioSnapshot, java.sql.Timestamp deductionDecidedAt,
			java.sql.Timestamp receivedAt) {
	}

	java.util.List<SelectPageRow> selectPage(@Param("q") Query q);

}
