package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_advertiser_change_audit")
public class AdxAdvertiserChangeAudit {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserId;

	private String action;

	private Long beforeConfigVersion;

	private Long afterConfigVersion;

	private Long credentialRevision;

	private Long documentRevision;

	private Integer affectedRouteCount;

	private String actor;

	private String changeSummary;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
