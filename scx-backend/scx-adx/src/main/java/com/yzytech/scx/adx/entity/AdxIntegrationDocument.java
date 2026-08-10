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
@TableName("scx_adx_integration_document")
public class AdxIntegrationDocument {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String ownerType;

	private String ownerId;

	private String documentType;

	@JsonIgnore
	private String storageConfigId;

	@JsonIgnore
	private String storageProvider;

	@JsonIgnore
	private String objectKey;

	private String fileName;

	private String contentType;

	private Long fileSize;

	private String sha256;

	private Long documentVersion;

	private String scanStatus;

	private String status;

	private String summary;

	private String createdBy;

	private String retiredBy;

	private LocalDateTime retiredAt;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
