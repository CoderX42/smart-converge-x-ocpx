package com.yzytech.scx.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.entity.MediaReportHourlyFact;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaReportHourlyFactMapper extends BaseMapper<MediaReportHourlyFact> {

	@Select("""
			SELECT *
			FROM scx_media_report_hourly_fact
			WHERE media_account_id=#{mediaAccountId}
			  AND external_campaign_id=#{externalCampaignId}
			  AND stat_hour_utc=#{statHourUtc}
			  AND sync_type=#{syncType}
			ORDER BY source_version DESC
			LIMIT 1
			""")
	MediaReportHourlyFact selectLatest(@Param("mediaAccountId") String mediaAccountId,
			@Param("externalCampaignId") String externalCampaignId, @Param("statHourUtc") LocalDateTime statHourUtc,
			@Param("syncType") String syncType);

	@Select("""
			SELECT MAX(pulled_at)
			FROM scx_media_report_hourly_fact
			WHERE media_id=#{mediaId} AND local_report_date=#{date}
			""")
	LocalDateTime selectWatermark(@Param("mediaId") String mediaId, @Param("date") java.time.LocalDate date);

}
