

package com.yzytech.scx.system.api.vo;

import com.baomidou.mybatisplus.annotation.*;
import com.yzytech.scx.common.core.annotation.Desensitization;
import com.yzytech.scx.common.core.desensitization.KeyDesensitization;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysSocialAccountVO {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "账号类型")
	private String type;

	@Schema(description = "账号")
	private String appId;

	@Schema(description = "密钥")
	@Desensitization(KeyDesensitization.class)
	private String appSecret;

	@Schema(description = "回调地址")
	private String redirectUrl;

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
	private String delFlag;

}
