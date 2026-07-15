

package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yzytech.scx.common.core.annotation.Desensitization;
import com.yzytech.scx.common.core.desensitization.MobilePhoneDesensitization;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 系统用户
 *
 * @since 2022/2/10 11:00
 */
@Data
@Schema(description = "系统用户")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user")
public class SysUser extends Model<SysUser> {

	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户名")
	private String username;

	@Schema(description = "密码")
	@JsonIgnore
	private String password;

	@Schema(description = "邮箱")
	private String email;

	@Schema(description = "用户昵称")
	private String nickname;

	@Schema(description = "头像")
	private String avatar;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "手机号")
	@Desensitization(MobilePhoneDesensitization.class)
	private String phone;

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
	private String delFlag;@Schema(description = "账号类型：0.系统主账户（管理全部店铺）；1.店铺账号（关联店铺）")
	private String type;

	@Schema(description = "店铺ID")
	private String shopId;

	@Schema(description = "微信openId")
	private String wxOpenId;

	@Schema(description = "微信小程序openId")
	private String wxMiniOpenId;

	/** 权限标识 */
	@TableField(exist = false)
	private Set<String> permissions;

	/** 角色 */
	@TableField(exist = false)
	private List<String> roles;

	/** 部门名称 */
	@TableField(exist = false)
	private String deptName;

}
