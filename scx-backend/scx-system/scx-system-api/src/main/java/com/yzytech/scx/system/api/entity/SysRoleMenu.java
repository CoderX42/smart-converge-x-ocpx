
package com.yzytech.scx.system.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 角色关联菜单
 *
 * @since 2022/2/10 11:00
 */
@Data
@Schema(description = "角色关联菜单")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_role_menu")
public class SysRoleMenu extends Model<SysRoleMenu> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "角色ID")
	private String roleId;

	@Schema(description = "菜单ID")
	private String menuId;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
