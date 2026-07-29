package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_ad_revision")
public class AdRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String adId;

	private Integer version;

	private String snapshot;

	private String supersedes;

	private LocalDateTime createdAt;

}
