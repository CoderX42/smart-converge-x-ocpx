package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_delivery_config_revision")
public class DeliveryConfigRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String resourceType;

	private String resourceId;

	private Integer configVersion;

	private String snapshotJson;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
