

package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统版权
 *
 * @date 2025/5/31
 */
@Data
@Schema(description = "系统版权")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_copyright")
public class SysCopyright extends Model<SysCopyright> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "logo地址")
	private String logoUrl;

	@Schema(description = "名称")
	private String name;

	@Schema(description = "版权信息")
	private String copyrightDesc;

	@Schema(description = "版权链接")
	private String copyrightLink;

	@Schema(description = "ICP备案号")
	private String icp;

	@Schema(description = "ICP网站链接")
	private String icpUrl;

	@Schema(description = "页面标题")
	private String pageTitle;

	@Schema(description = "页面副标题")
	private String pageDesc;

	@Schema(description = "页面图片")
	private String pagePic;

	@Schema(description = "客户端类型：1-用户端、2-商户端、3-平台端")
	private String clientType;

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
