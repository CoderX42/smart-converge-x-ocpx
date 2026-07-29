package com.yzytech.scx.campaign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_targeting_revision")
public class TargetingRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String targetingAst;

	private Integer version;

	private String supersedes;

	private LocalDateTime createdAt;

}
