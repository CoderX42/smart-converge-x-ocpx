package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_adx_hourly_report")
public class AdxHourlyReport {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private LocalDateTime timeHour;

	private String channelId;

	private String advertiserId;

	private String appLinkId;

	private String monitorId;

	private Long impressions;

	private Long clicks;

	private BigDecimal costAmount;

	private BigDecimal revenueAmount;

	private Long activateCount;

	private Long evokeCount;

	private Long loginCount;

	private Long registerCount;

	private Long paymentCount;

	private Long acquisitionCount;

	private Long nextStayCount;

	private Long visitCount;

	private Long authenticationCount;

	private Long orderCount;

	private Long firstOrderCount;

	private Long finishOrderCount;

	private Long rawConversionCount;

	private Long forwardedConversionCount;

	private Long deductedConversionCount;

	private Long deliveryFailureCount;

}
