package com.yzytech.scx.ocpx.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_advertiser")
public class OcpxAdvertiser {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserCode;

	private String advertiserName;

	private String adapterCode;

	private Long adapterVersion;

	private String bidUrl;

	@JsonIgnore
	private String secretKey;

	@JsonIgnore
	private String credentialRef;

	private Long credentialRevision;

	private String currentDocumentId;

	private Long documentRevision;

	@JsonIgnore
	private String createRequestKey;

	@JsonIgnore
	private String createRequestHash;

	private String macroParams;

	private String upstreamHostAllowlist;

	private String account;

	@JsonIgnore
	private String passwordHash;

	private BigDecimal balance;

	private String status;

	private Long configVersion;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
