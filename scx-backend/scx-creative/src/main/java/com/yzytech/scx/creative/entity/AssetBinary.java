package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_asset_binary")
public class AssetBinary {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String revisionId;

	private String storageKey;

	private String storageProvider;

	private Long contentLength;

	private String contentHash;

	private LocalDateTime createdAt;

}
