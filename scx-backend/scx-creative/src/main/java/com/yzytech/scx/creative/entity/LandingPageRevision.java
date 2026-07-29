package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_landing_page_revision")
public class LandingPageRevision {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String landingPageId;

	private Integer version;

	private String targetUrl;

	private String content;

	private Integer schemaVersion;

	private LocalDateTime createdAt;

}
