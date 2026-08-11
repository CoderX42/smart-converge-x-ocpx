package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_product_change_audit")
public class OcpxProductChangeAudit {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String productId;

	private String advertiserId;

	private String action;

	private Long beforeConfigVersion;

	private Long afterConfigVersion;

	private Long eventPolicyRevision;

	private Integer affectedAppLinkCount;

	private Integer affectedRouteCount;

	private String actor;

	private String changeSummary;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
