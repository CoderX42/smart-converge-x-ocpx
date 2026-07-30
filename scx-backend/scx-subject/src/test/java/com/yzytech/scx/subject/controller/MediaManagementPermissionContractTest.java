package com.yzytech.scx.subject.controller;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.subject.dto.MediaCreateRequest;
import com.yzytech.scx.subject.dto.MediaScopeGrantRequest;
import com.yzytech.scx.subject.dto.MediaStatusRequest;
import com.yzytech.scx.subject.dto.MediaUpdateRequest;
import org.junit.jupiter.api.Test;

class MediaManagementPermissionContractTest {

	@Test
	void mediaManagementRoutesRequireAdminOnlyPermission() throws Exception {
		assertPermission(MediaController.class, "manage", "subject:media:write",
				com.baomidou.mybatisplus.extension.plugins.pagination.Page.class, String.class, String.class);
		assertMutation(MediaController.class, "create", "subject:media:write", MediaCreateRequest.class);
		assertMutation(MediaController.class, "update", "subject:media:write", String.class, MediaUpdateRequest.class);
		assertMutation(MediaController.class, "changeStatus", "subject:media:write", String.class,
				MediaStatusRequest.class);
		assertMutation(MediaController.class, "delete", "subject:media:write", String.class, Integer.class);
	}

	@Test
	void mediaScopeRoutesSeparateReadAndWritePermissions() throws Exception {
		assertPermission(MediaScopeController.class, "page", "subject:media-scope:read",
				com.baomidou.mybatisplus.extension.plugins.pagination.Page.class, String.class, String.class);
		assertMutation(MediaScopeController.class, "grant", "subject:media-scope:write", MediaScopeGrantRequest.class);
		assertMutation(MediaScopeController.class, "revoke", "subject:media-scope:write", String.class, String.class);
	}

	private void assertMutation(Class<?> controller, String methodName, String permission, Class<?>... parameters)
			throws Exception {
		var method = controller.getDeclaredMethod(methodName, parameters);
		assertThat(method.getAnnotation(SysLog.class)).isNotNull();
		assertThat(method.getAnnotation(SaCheckPermission.class)).isNotNull();
		assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(permission);
	}

	private void assertPermission(Class<?> controller, String methodName, String permission, Class<?>... parameters)
			throws Exception {
		var annotation = controller.getDeclaredMethod(methodName, parameters).getAnnotation(SaCheckPermission.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).containsExactly(permission);
	}

}
