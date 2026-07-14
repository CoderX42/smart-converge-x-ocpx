

package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 行政区划
 *
 * @date 2025/5/3
 */
@Data
@Schema(description = "行政区划")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_region")
public class SysRegion extends Model<SysRegion> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "行政区划代码")
	private String code;

	@Schema(description = "区域名称")
	private String name;

	@Schema(description = "父级ID")
	private String parentId;

	@Schema(description = "层级：1-省，2-市，3-区/县")
	private String level;

	@Schema(description = "排序值")
	private Integer sort;

	@Schema(description = "状态")
	private String status;

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
