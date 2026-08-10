package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_app_link_change_audit")
public class AdxAppLinkChangeAudit {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String appLinkId;

	private String advertiserId;

	private String productId;

	private String action;

	private Long beforeConfigVersion;

	private Long afterConfigVersion;

	private String mappingStatus;

	private String templateHost;

	private String templateFingerprint;

	private Integer affectedRouteCount;

	private String actor;

	private String changeSummary;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
