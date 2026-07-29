package com.yzytech.scx.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_placement")
public class Placement {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaAccountId;

	private String placementCode;

	private String placementName;

	private String placementType;

	private String status;

	private String supportedCreatives;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
