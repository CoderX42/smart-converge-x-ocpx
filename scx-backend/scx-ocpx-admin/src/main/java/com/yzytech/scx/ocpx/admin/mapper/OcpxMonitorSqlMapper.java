package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxMonitorSqlMapper {

	int deleteDeleteRtaBinding(@Param("id") String id);

	int insertWriteBillingAuditBillingConfigAudit(
			@Param("command") InsertWriteBillingAuditBillingConfigAuditCommand command);

	record InsertWriteBillingAuditBillingConfigAuditCommand(String id, String monitorTaskId, String advertiserId,
			String actionType, String beforeConfig, String afterConfig, String operatorUserId,
			java.time.LocalDateTime occurredAt) {
	}

}
