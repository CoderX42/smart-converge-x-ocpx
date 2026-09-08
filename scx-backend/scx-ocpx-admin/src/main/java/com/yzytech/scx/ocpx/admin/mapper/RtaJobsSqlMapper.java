package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RtaJobsSqlMapper {

	int deleteCleanupLogsRtaLog();

	int insertLogs(@Param("rows") java.util.List<LogRow> rows);

	int upsertMetrics(@Param("values") MetricSnapshot values);

	record LogRow(String id, String relayRequestId, String requestId, String mediaId, String advertiserId,
			String upstreamId, String taskId, String stage, String status, String reason, long durationMs,
			java.sql.Timestamp eventTime) {
	}

	record MetricSnapshot(java.sql.Timestamp timeHour, String mediaId, String advertiserId, String taskId,
			String upstreamId, long requestCount, long allowCount, long denyCount, long errorCount, long timeoutCount,
			long deliverySuccessCount, long deliveryFailedCount, long version) {
	}

}
