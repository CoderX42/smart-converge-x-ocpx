package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_user_scope")
public class OcpxUserScope {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String userId;

	private String scopeType;

	private String scopeId;

	private String permissionLevel;

	private LocalDateTime validFrom;

	private LocalDateTime validTo;

	private String status;

	private Long version;

	private String createBy;

	private String updateBy;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
