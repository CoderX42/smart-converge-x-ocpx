package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxBillingEntrySqlMapper {

	record Query(String advertiserId, String taskId, String entryType, String status, String businessKey,
			java.time.LocalDateTime start, java.time.LocalDateTime end, long limit, long offset) {
	}

	Long count(@Param("q") Query q);

	record SelectPageRow(String id, String advertiserId, String advertiserName, String monitorTaskId,
			String monitorName, String eventFactId, String entryType, String status, java.math.BigDecimal amount,
			java.math.BigDecimal balanceBefore, java.math.BigDecimal balanceAfter, String businessKey,
			String reasonCode, String operatorUserId, String remark, java.sql.Timestamp occurredAt) {
	}

	java.util.List<SelectPageRow> selectPage(@Param("q") Query q);

}
