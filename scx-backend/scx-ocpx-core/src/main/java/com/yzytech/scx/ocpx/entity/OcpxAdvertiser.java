package com.yzytech.scx.ocpx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_advertiser")
public class OcpxAdvertiser {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserName;

	private OcpxUpstreamProtocol upstreamProtocol;

	private String bidUrl;

	private String credentialConfigKey;

	private String macroParams;

	private String accountUserId;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
