package com.yzytech.scx.ocpx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_monitor_task")
public class OcpxMonitorTask {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String monitorName;

	private String advertiserId;

	private String promotionLinkId;

	private String mediaId;

	/** RTA 强门禁开关；默认关闭，开启后点击必须先通过 RTA admission。 */
	private Boolean rtaEnabled;

	private Boolean billingEnabled;

	/** 1=click, 2=target conversion. */
	private Integer billingType;

	private BigDecimal unitPrice;

	/** 事件接收时冻结到事实表的主要转化事件。 */
	private String conversionEvent;

	private String bizId;

	private String showUrl;

	private String clkUrl;

	private String remark;

	@TableLogic
	@TableField("is_delete")
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
