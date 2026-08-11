package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_product_event_policy")
public class OcpxProductEventPolicy {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String productId;

	private String eventDefinitionId;

	private Integer dailyLimit;

	private String status;

	private Long version;

	private String createBy;

	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
