package com.yzytech.scx.adx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_adx_app_link")
public class AdxAppLink {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserId;

	private Integer linkPlatform;

	private String linkName;

	private String linkChannel;

	private String linkUrl;

	private String deeplink;

	private String linkExt;

	private String linkRemark;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
