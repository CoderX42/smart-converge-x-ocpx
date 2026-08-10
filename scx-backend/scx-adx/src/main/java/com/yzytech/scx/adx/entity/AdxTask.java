package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_task")
public class AdxTask {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String bizCode;

	private String advertiserId;

	private String productId;

	private String clickAppLinkId;

	private String exposureAppLinkId;

	private String channelId;

	private String mediaAccountId;

	private String mediaCredentialId;

	private Long mediaCredentialSecretRevision;

	private String operatorUserId;

	private String taskName;

	private String normalizedName;

	private Boolean exposureEnabled;

	private Boolean exposureAsClick;

	private Integer dedupWindowSeconds;

	private String rtaMode;

	private String description;

	private String status;

	private Long configVersion;

	private Boolean republishRequired;

	private String republishReason;

	private Long publishedTaskVersion;

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
