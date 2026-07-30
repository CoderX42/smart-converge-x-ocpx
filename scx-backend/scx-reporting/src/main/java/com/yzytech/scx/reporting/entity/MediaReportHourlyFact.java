package com.yzytech.scx.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_media_report_hourly_fact")
public class MediaReportHourlyFact {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String syncRunId;

	private String syncType;

	private String mediaAccountId;

	private String mediaId;

	private String channelId;

	private String publishingBindingId;

	private String campaignId;

	private String trackingTaskId;

	private String externalCampaignId;

	private LocalDateTime statHourUtc;

	private String sourceTimezone;

	private LocalDate localReportDate;

	private Integer localHour;

	private Long impressionCount;

	private Long clickCount;

	private Long conversionCount;

	private BigDecimal spendAmount;

	private String currency;

	private Integer sourceVersion;

	private String sourceHash;

	private String qualityCode;

	private LocalDateTime pulledAt;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
