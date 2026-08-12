package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_advertiser")
public class OcpxAdvertiser {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserCode;

	private String advertiserName;

	private String adapterCode;

	private String bidUrl;

	private String credentialConfigKey;

	private Long credentialVersion;

	private String macroParams;

	private String upstreamHostAllowlist;

	private String status;

	private Long configVersion;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
