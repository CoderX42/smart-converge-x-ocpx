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
			long version, boolean enabled, String protocol, String protocolConfig) {
		public SelectMediaByCredentialRtaMediaAccessRow(String mediaId, String notifyUrl, String credentialRef,
				long version, boolean enabled) {
			this(mediaId, notifyUrl, credentialRef, version, enabled, "GENERIC_ASYNC", null);
		}
	}

	record SelectBindingMonitorTaskRow(String id, String advertiserId, String mediaId, String promotionLinkId,
			String upstreamId, String productId, String externalTaskId, long version) {
	}

	record SelectTaskRequiresRtaMonitorTaskRow(boolean rtaEnabled) {
	}

	record SelectUpstreamRow(String id, String advertiserId, String accessCode, String endpoint, String credentialRef,
			long version, int timeoutMs, int admissionSeconds, String protocol, int configSchemaVersion,
			String protocolConfig) {
	}

	java.util.List<SelectUpstreamRow> selectUpstream(@Param("column") String column, @Param("value") String value);

	String selectTaskByStrategy(@Param("mediaId") String mediaId, @Param("strategy") int strategy);

	String selectOppoPkg(@Param("taskId") String taskId);

	String selectLink(@Param("id") String id);

}
