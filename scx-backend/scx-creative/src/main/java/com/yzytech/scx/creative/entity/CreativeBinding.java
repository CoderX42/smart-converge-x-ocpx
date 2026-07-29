package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_creative_binding")
public class CreativeBinding {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String adGroupId;

	private String creativeAssetId;

	private String revisionId;

	private String landingPageId;

	private String status;

	private LocalDateTime createTime;

}
