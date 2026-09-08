package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxAdvertiserAccountSqlMapper {

	int insertCreateAdvertiserAccount(@Param("advertiserId") String advertiserId);

	java.util.List<SelectBalanceAdvertiserAccountRow> selectBalanceAdvertiserAccount(
			@Param("advertiserId") String advertiserId);

	int updateRechargeAdvertiserAccount(@Param("balanceAfter") java.math.BigDecimal balanceAfter,
			@Param("advertiserId") String advertiserId);

	int insertRechargeBillingEntry(@Param("command") InsertRechargeBillingEntryCommand command);

	int updateDeductAdvertiserAccount(@Param("balanceAfter") java.math.BigDecimal balanceAfter,
			@Param("advertiserId") String advertiserId);

	int insertDeductBillingEntry(@Param("command") InsertDeductBillingEntryCommand command);

	Long selectAssertDeletableEventFact(@Param("advertiserId") String advertiserId);

	Long selectAssertDeletableBillingEntry(@Param("advertiserId") String advertiserId);

	Long selectAssertDeletableBillingConfigAudit(@Param("advertiserId") String advertiserId);

	int deleteDeleteEmptyAccountAdvertiserAccount(@Param("advertiserId") String advertiserId);

	java.util.List<SelectLockBalanceAdvertiserAccountRow> selectLockBalanceAdvertiserAccount(
			@Param("advertiserId") String advertiserId);

	java.util.List<SelectFindRechargeBillingEntryRow> selectFindRechargeBillingEntry(
			@Param("businessKey") String businessKey);

	java.util.List<SelectFindDeductionBillingEntryRow> selectFindDeductionBillingEntry(
			@Param("businessKey") String businessKey);

	record SelectBalanceAdvertiserAccountRow(java.math.BigDecimal availableBalance) {
	}

	record SelectLockBalanceAdvertiserAccountRow(java.math.BigDecimal availableBalance) {
	}

	record SelectFindRechargeBillingEntryRow(String id, String advertiserId, java.math.BigDecimal amount,
			java.math.BigDecimal balanceAfter, java.sql.Timestamp occurredAt) {
	}

	record SelectFindDeductionBillingEntryRow(String id, String advertiserId, java.math.BigDecimal amount,
			java.math.BigDecimal balanceAfter, java.sql.Timestamp occurredAt, String remark) {
	}

	record SelectBalancesByIdsRow(String advertiserId, java.math.BigDecimal availableBalance) {
	}

	java.util.List<SelectBalancesByIdsRow> selectBalancesByIds(@Param("ids") java.util.List<String> ids);

	record InsertRechargeBillingEntryCommand(String id, String advertiserId, java.math.BigDecimal amount,
			java.math.BigDecimal balanceBefore, java.math.BigDecimal balanceAfter, String businessKey,
			String operatorUserId, String remark, java.time.LocalDateTime occurredAt) {
	}

	record InsertDeductBillingEntryCommand(String id, String advertiserId, java.math.BigDecimal amount,
			java.math.BigDecimal balanceBefore, java.math.BigDecimal balanceAfter, String businessKey,
			String operatorUserId, String remark, java.time.LocalDateTime occurredAt) {
	}

}
