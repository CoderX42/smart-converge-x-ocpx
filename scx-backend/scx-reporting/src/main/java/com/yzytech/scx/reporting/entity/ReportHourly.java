package com.yzytech.scx.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_report_hourly")
public class ReportHourly {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String reportRunId;

	private LocalDateTime statHourUtc;

	private LocalDate reportDate;

	private Integer localHour;

	private String timezone;

	private String mediaId;

	private String mediaAccountId;

	private String campaignId;

	private String publishingBindingId;

	private String partnerId;

	private String customerId;

	private String productId;

	private String linkId;

	private String documentId;

	private String taskId;

	private String channelId;

	private Long mediaImpressionCount;

	private Long mediaClickCount;

	private Long mediaConversionCount;

	private Long uniqueClickCount;

	private Long eligibleClickCount;

	private Long deduplicatedClickCount;

	private Long attributedActivateCount;

	private Long attributedRegisterCount;

	private Long attributedPayCount;

	private Long attributedOrderCount;

	private Long nextDayReturnCount;

	private Long retention3dCount;

	private Long retention7dCount;

	private Long firstActivateCount;

	private Long firstOrderCount;

	private Long reactivateCount;

	private Long customCount;

	private Long creditCount;

	private Long callbackSucceededCount;

	private Long callbackFailedCount;

	private BigDecimal spendAmount;

	private String currency;

	private Integer revision;

	private String dataStatus;

	private String qualityCode;

	private LocalDateTime sourceWatermark;

	private LocalDateTime calculatedAt;

}
