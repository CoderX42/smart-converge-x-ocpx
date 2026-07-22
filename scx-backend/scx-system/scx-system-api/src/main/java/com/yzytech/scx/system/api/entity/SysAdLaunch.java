
package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 启动广告配置
 *
 * @date 2025/6/14
 */
@Data
@Schema(description = "启动广告配置")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_ad_launch")
public class SysAdLaunch extends Model<SysAdLaunch> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "广告图")
	private String adImgUrl;

	@Schema(description = "跳转地址")
	private String adToUrl;

	@Schema(description = "启动广告倒计时（秒）")
	private Integer countdownSeconds;

	@Schema(description = "倒计时按钮位置：top-left、top-right、bottom-left、bottom-right")
	private String countdownPosition;

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

	@Schema(description = "状态：0.正常；1.停用；")
	private String status;

}
