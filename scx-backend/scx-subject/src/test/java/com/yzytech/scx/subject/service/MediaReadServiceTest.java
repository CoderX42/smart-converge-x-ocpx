package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.mapper.MediaMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaReadServiceTest {

	@Test
	void nonAdminCanOnlyReadMapperScopedMedia() {
		MediaMapper mapper = mock(MediaMapper.class);
		Media media = new Media();
		media.setId("media-a");
		when(mapper.findAuthorized("user-a")).thenReturn(List.of(media));
		MediaReadService service = new MediaReadService(mapper);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("user-a", "ROLE_OPERATOR"));
			assertThat(service.listForCurrentUser()).containsExactly(media);
		}

		verify(mapper).findAuthorized("user-a");
		verify(mapper, never()).selectList(any());
	}

	@Test
	void adminCanReadAllActiveMedia() {
		MediaMapper mapper = mock(MediaMapper.class);
		Media media = new Media();
		media.setId("media-a");
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(media));
		MediaReadService service = new MediaReadService(mapper);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("admin", "ROLE_ADMIN"));
			assertThat(service.listForCurrentUser()).containsExactly(media);
		}

		verify(mapper).selectList(any(Wrapper.class));
		verify(mapper, never()).findAuthorized(anyString());
	}

	@Test
	void nonAdminDetailUsesUserAndSubjectScopeTogether() {
		MediaMapper mapper = mock(MediaMapper.class);
		Media media = new Media();
		media.setId("media-a");
		when(mapper.findAuthorizedById("user-a", "media-a")).thenReturn(java.util.Optional.of(media));
		MediaReadService service = new MediaReadService(mapper);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("user-a", "ROLE_OPERATOR"));
			assertThat(service.getForCurrentUser("media-a")).contains(media);
		}

		verify(mapper).findAuthorizedById("user-a", "media-a");
		verify(mapper, never()).selectById(anyString());
	}

	private BootUser user(String userId, String role) {
		BootUser user = new BootUser();
		user.setUserId(userId);
		user.setRoles(List.of(role));
		return user;
	}
}
