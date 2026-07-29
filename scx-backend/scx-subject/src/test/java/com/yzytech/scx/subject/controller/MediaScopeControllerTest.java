package com.yzytech.scx.subject.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.subject.dto.MediaScopeUserOption;
import com.yzytech.scx.subject.service.MediaScopeManagementService;
import org.junit.jupiter.api.Test;

class MediaScopeControllerTest {

	private final MediaScopeManagementService service = mock(MediaScopeManagementService.class);

	private final MediaScopeController controller = new MediaScopeController(service);

	@Test
	void candidateEndpointUsesScopeWritePermission() throws Exception {
		SaCheckPermission permission = MediaScopeController.class
			.getDeclaredMethod("candidates", Page.class, String.class, String.class)
			.getAnnotation(SaCheckPermission.class);

		assertThat(permission).isNotNull();
		assertThat(permission.value()).containsExactly("subject:media-scope:write");
	}

	@Test
	void candidateEndpointReturnsMinimalProjectionPage() {
		Page<MediaScopeUserOption> page = new Page<>(1, 20);
		MediaScopeUserOption option = new MediaScopeUserOption();
		option.setId("user-1");
		option.setUsername("operator");
		option.setNickname("运营");
		page.setRecords(java.util.List.of(option));
		when(service.candidates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("media-1"),
				org.mockito.ArgumentMatchers.isNull()))
			.thenReturn(page);

		var result = controller.candidates(new Page<>(1, 20), "media-1", null);

		assertThat(result.getCode()).isZero();
		assertThat(result.getData().getRecords()).singleElement().satisfies(user -> {
			assertThat(user.getId()).isEqualTo("user-1");
			assertThat(user.getUsername()).isEqualTo("operator");
			assertThat(user.getNickname()).isEqualTo("运营");
		});
	}

	@Test
	void selfRevokeReturnsActionableConflict() {
		when(service.revoke("scope-1", "media-1")).thenThrow(new IllegalStateException("不能撤销自己的媒体范围"));

		var result = controller.revoke("scope-1", "media-1");

		assertThat(result.getCode()).isEqualTo(409);
		assertThat(result.getMsg()).isEqualTo("不能撤销自己的媒体范围");
	}

}
