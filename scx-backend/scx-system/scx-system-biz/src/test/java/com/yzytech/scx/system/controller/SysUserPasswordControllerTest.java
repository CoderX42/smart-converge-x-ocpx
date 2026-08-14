package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.crypto.digest.BCrypt;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.dto.SysUserDTO;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.service.ISysRoleService;
import com.yzytech.scx.system.service.ISysUserRoleService;
import com.yzytech.scx.system.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserPasswordControllerTest {

	@Test
	void selfPasswordChangeIgnoresRequestedUserIdAndUsesAuthenticatedPrincipal() throws Exception {
		ISysUserService userService = mock(ISysUserService.class);
		SysUserController controller = controller(userService);
		SysUser current = new SysUser();
		current.setId("current-user");
		current.setPassword(BCrypt.hashpw("Old1!x"));
		when(userService.getById("current-user")).thenReturn(current);
		SysUserDTO request = new SysUserDTO();
		request.setId("another-user");
		request.setPassword("Old1!x");
		request.setNewPassword("New2!x");
		request.setCheckPassword("New2!x");

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserId).thenReturn("current-user");
			assertThat(controller.editPwd(request).getCode()).isZero();
		}

		verify(userService).getById("current-user");
		verify(userService, never()).getById("another-user");
		ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).updateById(captor.capture());
		assertThat(BCrypt.checkpw("New2!x", captor.getValue().getPassword())).isTrue();
		assertThat(
				SysUserController.class.getMethod("editPwd", SysUserDTO.class).getAnnotation(SaCheckPermission.class))
			.isNull();
		assertThat(SysUserController.class.getMethod("resetPassword", SysUserDTO.class)
			.getAnnotation(SaCheckPermission.class)
			.value()).containsExactly("system:sysuser:password");
	}

	@Test
	void selfPasswordChangeRejectsWeakNewPassword() {
		ISysUserService userService = mock(ISysUserService.class);
		SysUserController controller = controller(userService);
		SysUser current = new SysUser();
		current.setId("current-user");
		current.setPassword(BCrypt.hashpw("Old1!x"));
		when(userService.getById("current-user")).thenReturn(current);
		SysUserDTO request = new SysUserDTO();
		request.setPassword("Old1!x");
		request.setNewPassword("abcdef");
		request.setCheckPassword("abcdef");

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUserId).thenReturn("current-user");
			assertThat(controller.editPwd(request).getMsg()).contains("字母、数字和特殊字符");
		}
		verify(userService, never()).updateById(current);
	}

	private SysUserController controller(ISysUserService userService) {
		return new SysUserController(userService, mock(ISysRoleService.class), mock(ISysUserRoleService.class),
				mock(StringRedisTemplate.class));
	}

}
