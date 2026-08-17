package com.yzytech.scx.ocpx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_creative")
public class OcpxCreative {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserId;

	private String creativeName;

	private Integer creativeType;

	private String assetUrl;

	private String sourceMaterialId;

	private String fileName;

	private String mimeType;

	private Long fileSize;

	private Integer width;

	private Integer height;

	private String checksumSha256;

	private Integer status;

	private String remark;

	@TableLogic
	@TableField("is_delete")
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
