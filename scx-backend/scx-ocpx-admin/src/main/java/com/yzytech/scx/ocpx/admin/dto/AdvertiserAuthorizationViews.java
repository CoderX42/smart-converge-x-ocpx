package com.yzytech.scx.ocpx.admin.dto;

import java.util.List;

public final class AdvertiserAuthorizationViews {

	private AdvertiserAuthorizationViews() {
	}

	public record Role(String id, String name, String code, List<String> menuIds) {
	}

	public record Menu(String id, String parentId, String name, String type, String permission, boolean grantable,
			List<Menu> children) {
	}

	public record Options(Role mandatoryRole, List<Role> sharedRoles, List<Menu> menuTree) {
	}

	public record Detail(String advertiserId, String userId, String accountMasked, Role mandatoryRole,
			List<String> roleIds, List<String> inheritedMenuIds, List<String> extraMenuIds,
			List<String> effectiveMenuIds, Long grantVersion) {
	}

}
