package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_channel")
public class OcpxChannel {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String channelCode;

	private String channelName;

	private String adapterCode;

	private String authType;

	private Integer keyVersion;

	private String uri;

	@JsonIgnore
	private String secretKey;

	private String macroParams;

	private String callbackHostAllowlist;

	private String status;

	private Long configVersion;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
