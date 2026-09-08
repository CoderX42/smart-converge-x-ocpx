package com.yzytech.scx.ocpx.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RelaySqlMapper {

	java.util.List<SelectMediaByCredentialRtaMediaAccessRow> selectMediaByCredentialRtaMediaAccess(
			@Param("ref") String ref);

	java.util.List<SelectBindingMonitorTaskRow> selectBindingMonitorTask(@Param("id") String id,
			@Param("mediaId") String mediaId);

	java.util.List<SelectTaskRequiresRtaMonitorTaskRow> selectTaskRequiresRtaMonitorTask(
			@Param("taskId") String taskId);

	record SelectMediaByCredentialRtaMediaAccessRow(String mediaId, String notifyUrl, String credentialRef,
			long version, boolean enabled) {
	}

	record SelectBindingMonitorTaskRow(String id, String advertiserId, String mediaId, String promotionLinkId,
			String upstreamId, String productId, String externalTaskId, long version) {
	}

	record SelectTaskRequiresRtaMonitorTaskRow(boolean rtaEnabled) {
	}

	record SelectUpstreamRow(String id, String advertiserId, String accessCode, String endpoint, String credentialRef,
			long version, int timeoutMs, int admissionSeconds) {
	}

	java.util.List<SelectUpstreamRow> selectUpstream(@Param("column") String column, @Param("value") String value);

}
