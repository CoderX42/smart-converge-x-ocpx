package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_app_link")
public class AdxAppLink {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String bizCode;

	private String advertiserId;

	private String productId;

	private String documentId;

	private Long documentVersion;

	private Integer linkPlatform;

	private String linkType;

	private String linkName;

	private String normalizedName;

	private String linkChannel;

	@JsonIgnore
	private String linkUrl;

	@JsonIgnore
	private String linkUrlTemplate;

	private String templateHost;

	@JsonIgnore
	private String templateFingerprint;

	private String templateMacroSet;

	private Boolean redirectEnabled;

	@JsonIgnore
	private String deeplink;

	@JsonIgnore
	private String deeplinkUrl;

	@JsonIgnore
	private String downloadUrl;

	private String linkExt;

	private String linkRemark;

	private String description;

	private String status;

	private String mappingStatus;

	private Long configVersion;

	private String createRequestKey;

	@JsonIgnore
	private String createRequestHash;

	private String archivedBy;

	private LocalDateTime archivedAt;

	private String createBy;

	private String updateBy;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
