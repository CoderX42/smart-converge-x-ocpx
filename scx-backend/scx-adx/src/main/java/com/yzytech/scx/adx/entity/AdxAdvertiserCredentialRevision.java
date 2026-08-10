package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_advertiser_credential_revision")
public class AdxAdvertiserCredentialRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserId;

	@JsonIgnore
	private String credentialRef;

	private Long credentialRevision;

	@JsonIgnore
	private String secretCiphertext;

	private String storageMode;

	private String status;

	private LocalDateTime overlapUntil;

	private String reason;

	private String createdBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
