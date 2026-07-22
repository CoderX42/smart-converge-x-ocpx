package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_delivery_status_history")
public class DeliveryStatusHistory {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String resourceType;

	private String resourceId;

	private String previousStatus;

	private String nextStatus;

	private String reason;

	private Integer operationVersion;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
