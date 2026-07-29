package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_ad_group_revision")
public class AdGroupRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String adGroupId;

	private Integer version;

	private String snapshot;

	private String supersedes;

	private LocalDateTime createdAt;

}
