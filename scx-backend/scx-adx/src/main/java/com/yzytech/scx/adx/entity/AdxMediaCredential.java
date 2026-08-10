package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_media_credential")
public class AdxMediaCredential {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaAccountId;

	private String loginType;

	private String loginIdentifierCiphertext;

	private String loginIdentifierHash;

	private String maskedIdentifier;

	private String currentCredentialRef;

	private Long currentSecretRevision;

	private String description;

	private String status;

	private Long version;

	private String lastPreflightStatus;

	private LocalDateTime lastPreflightAt;

	private String createBy;

	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
