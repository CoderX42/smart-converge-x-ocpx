package com.yzytech.scx.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_media_account")
public class MediaAccount {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaId;

	private String channelId;

	private String accountName;

	private String accountIdExternal;

	private String status;

	private String currency;

	private String timezone;

	private String adapterType;

	@JsonIgnore
	private String adapterConfig;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
