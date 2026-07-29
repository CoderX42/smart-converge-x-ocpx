package com.yzytech.scx.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_integration_document")
public class IntegrationDocument {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@NotBlank
	private String mediaId;

	@NotBlank
	@Size(max = 128)
	private String name;

	@Size(max = 500)
	private String description;

	private String type;

	private Integer documentVersion;

	private String storageType;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String objectKey;

	private String externalUrl;

	private String originalFileName;

	private String mimeType;

	private Long fileSize;

	@JsonIgnore
	private String storageConfigId;

	@JsonIgnore
	private String storageProvider;

	@JsonIgnore
	private String storageObjectKey;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String contentHash;

	private String status;

	@Version
	private Integer version;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
