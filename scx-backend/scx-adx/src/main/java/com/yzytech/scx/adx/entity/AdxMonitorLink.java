package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_adx_monitor_link")
public class AdxMonitorLink {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String channelId;

	private String advertiserId;

	private String appLinkId;

	private String monitorName;

	private String bizId;

	private BigDecimal unitPrice;

	private String remark;

	private String showUrl;

	private String clickUrl;

	private String upstreamFeedbackUrl;

	private String publishedUpstreamFeedbackUrl;

	private String downstreamCallbackUrl;

	private String routeStatus;

	private Long configVersion;

	private String rtaMode;

	private String billingMode;

	private LocalDateTime publishedAt;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
