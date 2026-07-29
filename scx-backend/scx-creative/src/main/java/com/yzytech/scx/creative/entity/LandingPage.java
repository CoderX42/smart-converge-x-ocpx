package com.yzytech.scx.creative.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_landing_page")
public class LandingPage {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orgId;

	private String pageName;

	private String pageType;

	private String targetUrl;

	private Integer schemaVersion;

	private String content;

	private String status;

	private String currentRevisionId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
