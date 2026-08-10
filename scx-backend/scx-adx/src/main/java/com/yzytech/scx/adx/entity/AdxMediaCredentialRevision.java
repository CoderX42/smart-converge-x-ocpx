package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_media_credential_revision")
public class AdxMediaCredentialRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String credentialId;

	private String credentialRef;

	private Long secretRevision;

	private String secretCiphertext;

	private String status;

	private LocalDateTime overlapUntil;

	private String reason;

	private String createdBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
