package com.yzytech.scx.tracking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scx_channel_client")
public class ChannelClient {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaId;

	private String clientId;

	private String sourceCode;

	private String status;

	private Integer maxQps;

	private Boolean dynamicCallbackEnabled;

	private String callbackAllowedHosts;

	private String currentKeyVersion;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
