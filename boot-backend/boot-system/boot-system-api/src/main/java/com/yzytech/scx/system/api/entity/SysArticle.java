

package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文章
 *
 * @date 2025/5/26
 */
@Data
@Schema(description = "文章")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_article")
public class SysArticle extends Model<SysArticle> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "文章内容")
	private String content;

	@Schema(description = "标题")
	private String title;

	@Schema(description = "作者")
	private String author;

	@Schema(description = "封面图")
	private String coverUrl;

	@Schema(description = "排序序号")
	private Integer sort;

	@Schema(description = "分类ID")
	private String categoryId;

	@Schema(description = "状态：0.正常；1.停用；")
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
	private String delFlag;}
