package com.yzytech.scx.adx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.ReportRow;
import com.yzytech.scx.adx.entity.AdxHourlyReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdxHourlyReportMapper extends BaseMapper<AdxHourlyReport> {

	Page<ReportRow> summary(Page<ReportRow> page, @Param("granularity") String granularity,
			@Param("channelId") String channelId, @Param("monitorId") String monitorId,
			@Param("advertiserId") String advertiserId, @Param("appLinkId") String appLinkId,
			@Param("allowedAdvertiserIds") List<String> allowedAdvertiserIds,
			@Param("allowedChannelIds") List<String> allowedChannelIds, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end);

	Map<String, Object> accountSummary(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			@Param("channelId") String channelId, @Param("advertiserId") String advertiserId,
			@Param("appLinkId") String appLinkId, @Param("monitorId") String monitorId,
			@Param("allowedAdvertiserIds") List<String> allowedAdvertiserIds,
			@Param("allowedChannelIds") List<String> allowedChannelIds);

}
