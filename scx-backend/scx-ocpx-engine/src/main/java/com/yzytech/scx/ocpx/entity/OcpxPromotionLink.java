package com.yzytech.scx.ocpx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scx_ocpx_promotion_link")
public class OcpxPromotionLink {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String advertiserId;

	private String linkCode;

	private String linkName;

	private String packageName;

	private String platform;

	private String linkType;

	private String linkChannel;

	private String linkUrlTemplate;

	private String deeplinkUrl;

	private String downloadUrl;

	private String macroSet;

	private Boolean redirectEnabled;

	private String remark;

	private String status;

	private Long configVersion;

	@TableLogic
	private Boolean deleted;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
