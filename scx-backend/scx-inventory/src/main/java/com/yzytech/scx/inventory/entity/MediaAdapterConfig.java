package com.yzytech.scx.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_media_adapter_config")
public class MediaAdapterConfig {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String mediaAccountId;

	private String adapterType;

	private String endpointUrl;

	@JsonIgnore
	private String authConfig;

	private Integer rateLimitQps;

	private Integer timeoutMs;

	private String retryConfig;

	private String status;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
