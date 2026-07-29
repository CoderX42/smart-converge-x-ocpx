package com.yzytech.scx.commercial.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scx_ad_account")
public class AdAccount {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserOrgId;

	private String accountName;

	private String status;

	private String currency;

	private String timezone;

	private String responsibleOrgId;

	private String rebateOrgId;

	private String refundOrgId;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
