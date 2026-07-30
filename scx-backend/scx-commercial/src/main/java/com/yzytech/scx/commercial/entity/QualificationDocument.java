package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_qualification_document")
public class QualificationDocument {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String caseId;

	private String docType;

	private String docName;

	private String fileUrl;

	private String originalFileName;

	private String mimeType;

	private Long fileSize;

	private String storageConfigId;

	private String storageProvider;

	private String storageObjectKey;

	private String contentHash;

	private String status;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
