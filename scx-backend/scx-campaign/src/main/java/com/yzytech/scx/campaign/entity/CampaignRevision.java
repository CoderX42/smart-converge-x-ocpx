package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_campaign_revision")
public class CampaignRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String campaignId;

	private Integer version;

	private String snapshot;

	private String supersedes;

	private LocalDateTime createdAt;

}
