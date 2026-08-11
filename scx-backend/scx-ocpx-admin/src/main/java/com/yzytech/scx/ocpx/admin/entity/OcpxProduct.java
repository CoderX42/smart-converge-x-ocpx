package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_product")
public class OcpxProduct {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserId;

	private String code;

	private String name;

	private String normalizedName;

	private String packageName;

	private String logoObjectId;

	private String description;

	private String status;

	private Long configVersion;

	private Long eventPolicyRevision;

	private String createRequestKey;

	private String createRequestHash;

	private String archivedBy;

	private LocalDateTime archivedAt;

	private String createBy;

	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
