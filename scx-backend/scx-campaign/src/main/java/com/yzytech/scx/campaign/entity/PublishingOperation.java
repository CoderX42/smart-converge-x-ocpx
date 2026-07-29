package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_publishing_operation")
public class PublishingOperation {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String campaignId;

	private String operationType;

	private String status;

	private String requestSnapshot;

	private String resultDetail;

	private LocalDateTime createdAt;

	private LocalDateTime completedAt;

}
