package com.yzytech.scx.ocpx.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlowLogRetentionSqlMapper {

	Integer selectPreviewRetentionReadyInformationSchema();

	int deleteExpiredDetails(@Param("limit") int limit);

	int deleteExpiredFeedback(@Param("limit") int limit);

	int deleteExpiredCallbacks(@Param("limit") int limit);

	int deleteExpiredMetadata(@Param("limit") int limit);

	record SelectExpiredPreviewsRow(String id, java.sql.Timestamp eventTime) {
	}

	java.util.List<SelectExpiredPreviewsRow> selectExpiredPreviews(
			@Param("eventTime") java.time.LocalDateTime eventTime, @Param("id") String id, @Param("limit") int limit);

	int clearPreviews(@Param("ids") java.util.List<Object> ids);

}
