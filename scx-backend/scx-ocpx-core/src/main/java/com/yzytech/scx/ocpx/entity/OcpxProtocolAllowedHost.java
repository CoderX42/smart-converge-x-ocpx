package com.yzytech.scx.ocpx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import lombok.Data;

import java.time.LocalDateTime;

/** 后台维护的上游协议精确域名白名单；表中不保存 URL、路径或凭据。 */
@Data
@TableName("scx_ocpx_protocol_allowed_host")
public class OcpxProtocolAllowedHost {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private OcpxUpstreamProtocol protocolType;

	private String host;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
