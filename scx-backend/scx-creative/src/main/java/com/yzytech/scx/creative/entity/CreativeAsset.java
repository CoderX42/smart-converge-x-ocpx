package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_creative_asset")
public class CreativeAsset {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaId;

	private String orgId;

	private String assetType;

	private String assetName;

	private String fileUrl;

	private String mimeType;

	private Long fileSize;

	private String fileHash;

	private Integer width;

	private Integer height;

	private Integer durationMs;

	private String status;

	private String currentRevisionId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
