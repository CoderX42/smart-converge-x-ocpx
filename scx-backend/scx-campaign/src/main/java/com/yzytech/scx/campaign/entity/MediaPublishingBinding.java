package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_media_publishing_binding")
public class MediaPublishingBinding {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String campaignId;

	private String mediaAccountId;

	private String placementId;

	private String trackingTaskId;

	private String adGroupId;

	private String externalCampaignId;

	private String externalStatus;

	private LocalDateTime effectiveFrom;

	private LocalDateTime effectiveTo;

	private String bindingStatus;

	@Version
	private Integer version;

	private LocalDateTime lastSyncedAt;

}
