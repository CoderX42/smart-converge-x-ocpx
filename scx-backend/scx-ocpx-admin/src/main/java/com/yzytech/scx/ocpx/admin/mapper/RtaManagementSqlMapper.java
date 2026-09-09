package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RtaManagementSqlMapper {

	int insertSaveRtaUpstream(@Param("command") InsertSaveRtaUpstreamCommand command);

	int updateSaveRtaUpstream(@Param("command") UpdateSaveRtaUpstreamCommand command);

	int insertSaveRtaMediaAccess(@Param("media") String media, @Param("notify") String notify, @Param("ref") String ref,
			@Param("enabled") boolean enabled);

	int updateSaveRtaMediaAccess(@Param("notify") String notify, @Param("ref") String ref,
			@Param("enabled") boolean enabled, @Param("media") String media, @Param("version") long version);

	java.util.List<java.util.Map<String, Object>> selectSaveRtaMediaAccess(@Param("value1") Object value1);

	Long selectSaveRtaBinding(@Param("value1") Object value1, @Param("upstream") String upstream,
			@Param("product") String product, @Param("external") String external, @Param("task") String task);

	int insertSaveRtaBinding(@Param("task") String task, @Param("upstream") String upstream,
			@Param("product") String product, @Param("external") String external, @Param("enabled") boolean enabled);

	int updateSaveRtaBinding(@Param("command") UpdateSaveRtaBindingCommand command);

	int updateSaveMonitorTask(@Param("enabled") boolean enabled, @Param("task") String task);

	record Query(String kind, String advertiserId, java.util.Map<String, String> filters, java.sql.Timestamp start,
			java.sql.Timestamp end, java.sql.Timestamp retentionStart, long limit, long offset) {
	}

	Long count(@Param("q") Query q);

	String dataAsOf(@Param("q") Query q);

	java.util.List<java.util.Map<String, Object>> selectPage(@Param("q") Query q);

	java.util.List<java.util.Map<String, Object>> selectOptions(@Param("q") Query q);

	java.util.List<java.util.Map<String, Object>> lockTask(@Param("id") String id);

	Long countValidRoute(@Param("mediaId") Object mediaId, @Param("upstreamId") String upstreamId,
			@Param("advertiserId") Object advertiserId, @Param("enabled") boolean enabled);

	java.util.List<java.util.Map<String, Object>> selectMediaNames(@Param("ids") java.util.Set<Object> ids);

	Long countAdvertiser(@Param("id") String id);

	Long countMedia(@Param("id") String id);

	record InsertSaveRtaUpstreamCommand(String id, String advertiserId, String name, String accessCode, String endpoint,
			String credentialRef, int timeoutMs, Integer admissionSeconds, boolean enabled, String protocol,
			String protocolConfig) {
	}

	record UpdateSaveRtaUpstreamCommand(String name, String endpoint, String credentialRef, int timeoutMs,
			Integer admissionSeconds, boolean enabled, String id, String advertiserId, String accessCode, long version,
			String protocol, String protocolConfig) {
	}

	record UpdateSaveRtaBindingCommand(String upstreamId, String productId, String externalTaskId, boolean enabled,
			String monitorTaskId, long version) {
	}

	java.util.Map<String, Object> findUpstream(@Param("id") String id);

	int updateMediaProtocol(@Param("media") String media, @Param("protocol") String protocol,
			@Param("config") String config);

	int updateOppoPkg(@Param("task") String task, @Param("pkg") String pkg);

	String selectLink(@Param("id") Object id);

	java.util.Map<String, Object> selectRtaTestRoute(@Param("id") String id);

}
