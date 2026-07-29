package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_creative_asset_revision")
public class CreativeAssetRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String assetId;

	private Integer version;

	private String fileUrl;

	private String mimeType;

	private Long fileSize;

	private String fileHash;

	private Integer width;

	private Integer height;

	private Integer durationMs;

	private LocalDateTime createdAt;

}
