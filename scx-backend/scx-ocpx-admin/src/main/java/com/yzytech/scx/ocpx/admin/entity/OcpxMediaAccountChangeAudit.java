package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_media_account_change_audit")
public class OcpxMediaAccountChangeAudit {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaAccountId;

	private String action;

	private Long beforeConfigVersion;

	private Long afterConfigVersion;

	private Long credentialRevision;

	private Integer ownerCount;

	private Integer affectedRouteCount;

	private String actor;

	private String changeSummary;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
