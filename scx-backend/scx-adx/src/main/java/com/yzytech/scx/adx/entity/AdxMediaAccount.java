package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_media_account")
public class AdxMediaAccount {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String channelId;

	private String code;

	private String name;

	private String normalizedName;

	private String documentId;

	private Long documentVersion;

	private String description;

	private String defaultCredentialId;

	private String status;

	private Long configVersion;

	private Long credentialRevision;

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
