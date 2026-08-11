package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_product_logo_object")
public class OcpxProductLogoObject {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String storageConfigId;

	private String storageProvider;

	private String objectKey;

	private String fileName;

	private String contentType;

	private Long fileSize;

	private String sha256;

	private Integer imageWidth;

	private Integer imageHeight;

	private String status;

	private String createdBy;

	private LocalDateTime retiredAt;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
