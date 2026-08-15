package com.yzytech.scx.system.api.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 系统托管的共享广告主角色权限管理端口。
 *
 * <p>
 * OCPX 管理面通过该端口读取和更新固定 {@code ROLE_ADVERTISER}，系统模块负责保护角色、菜单和登录会话。
 * </p>
 */
public interface AdvertiserRolePermissionManager {

	/** 返回共享角色的当前权限配置。 */
	Configuration getConfiguration();

	/**
	 * 全量替换共享角色权限。
	 * @param menuIds 选中的 OCPX 菜单和按钮主键，不包含全量数据范围节点
	 * @param allDataScope 是否授予全部 OCPX 数据范围
	 */
	Configuration updateConfiguration(List<String> menuIds, boolean allDataScope);

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	class Configuration {

		private String roleId;

		private String roleName;

		private String roleCode;

		private List<String> selectedMenuIds;

		private boolean allDataScope;

		private long affectedAccountCount;

		private List<MenuNode> menuTree;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	class MenuNode {

		private String id;

		private String parentId;

		private String name;

		private String type;

		private String permission;

		private boolean requiresAllDataScope;

		private List<MenuNode> children;

	}

}
