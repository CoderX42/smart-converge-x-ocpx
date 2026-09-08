package com.yzytech.scx.ocpx.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TestCallbackCollectorSqlMapper {

	int insertArmFlowLog(@Param("command") InsertArmFlowLogCommand command);

	Long selectRecordFlowLog(@Param("requestId") String requestId, @Param("mediaId") String mediaId,
			@Param("monitorTaskId") String monitorTaskId, @Param("advertiserId") String advertiserId,
			@Param("promotionLinkId") String promotionLinkId, @Param("armedHash") String armedHash);

	Long selectRecordEventFact(@Param("requestId") String requestId, @Param("mediaId") String mediaId,
			@Param("monitorTaskId") String monitorTaskId, @Param("advertiserId") String advertiserId,
			@Param("promotionLinkId") String promotionLinkId, @Param("name") String name);

	int insertRecordFlowLog(@Param("command") InsertRecordFlowLogCommand command);

	record InsertArmFlowLogCommand(String id, String flowType, String eventId, String sourceEventId, String eventCode,
			String payloadHash, String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId,
			String resultStatus, Integer partnerHttpStatus, String message, long durationMs,
			java.time.LocalDateTime eventTime) {
	}

	record InsertRecordFlowLogCommand(String id, String flowType, String eventId, String sourceEventId,
			String eventCode, String payloadHash, String advertiserId, String promotionLinkId, String mediaId,
			String monitorTaskId, String resultStatus, Integer partnerHttpStatus, String message, long durationMs,
			java.time.LocalDateTime eventTime) {
	}

}
