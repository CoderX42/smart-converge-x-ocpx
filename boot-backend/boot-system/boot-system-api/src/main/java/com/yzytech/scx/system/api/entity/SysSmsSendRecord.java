

package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 短信发送记录
 *
 * @date 2025/7/03
 */
@Data
@Schema(description = "短信发送记录")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_sms_send_record")
public class SysSmsSendRecord extends Model<SysSmsSendRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "接收手机号")
	private String phone;

	@Schema(description = "业务用途标识")
	private String smsCode;

	@Schema(description = "短信内容")
	private String content;

	@Schema(description = "发送状态：0待发 1成功 2失败")
	private String sendStatus;

	@Schema(description = "发送时间")
	private LocalDateTime sendTime;

	@Schema(description = "失败原因")
	private String errorMsg;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;}
