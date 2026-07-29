package com.yzytech.scx.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_media_capability")
public class MediaCapability {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String placementId;

	private String billingModes;

	private String targetEvents;

	private String targetingFields;

	private String creativeFormats;

	private Integer mediaManagedOcpc;

	private Integer statusSyncSupported;

	private Integer reportPullSupported;

	private Long dailyImpressionCap;

	private Long dailyClickCap;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
