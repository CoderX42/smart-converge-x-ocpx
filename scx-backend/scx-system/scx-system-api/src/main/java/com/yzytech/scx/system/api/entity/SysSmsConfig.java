

package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.yzytech.scx.common.core.annotation.Desensitization;
import com.yzytech.scx.common.core.desensitization.KeyDesensitization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 短信配置
 *
 * @date 2025/7/02
 */
@Data
@Schema(description = "短信配置")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_sms_config")
public class SysSmsConfig extends Model<SysSmsConfig> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "厂商标识")
	private String supplier;

	@Schema(description = "accessKeyId")
	@NotNull(message = "accessKeyId不能为空")
	@Desensitization(KeyDesensitization.class)
	private String accessKeyId;

	@Schema(description = "accessKeySecret")
	@NotNull(message = "accessKeyId不能为空")
	@Desensitization(KeyDesensitization.class)
	private String accessKeySecret;

	@Schema(description = "短信签名")
	private String signature;

	@Schema(description = "模板ID")
	private String templateId;

	@Schema(description = "额外参数")
	private String extraParams;

	@Schema(description = "短信名称")
	private String name;

	@Schema(description = "短信编码")
	private String smsCode;

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
	private String delFlag;@Schema(description = "状态：0.正常；1.停用；")
	private String status;

}
