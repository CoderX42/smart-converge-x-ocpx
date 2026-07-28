package com.yzytech.scx.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.subject.dto.MediaScopeGrantRequest;
import com.yzytech.scx.subject.entity.Media;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.MediaMapper;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class MediaScopeManagementServiceTest {

	@Mock
	private UserSubjectScopeMapper scopeMapper;

	@Mock
	private MediaMapper mediaMapper;

	@Test
	void grantsActiveUserToActiveMedia() {
		MediaScopeManagementService service = new MediaScopeManagementService(scopeMapper, mediaMapper);
		when(mediaMapper.selectById("media-1")).thenReturn(media("ACTIVE"));
		when(scopeMapper.countActiveUser("user-1")).thenReturn(1);
		when(scopeMapper.selectOne(any())).thenReturn(null);
		when(scopeMapper.insert(any(UserSubjectScope.class))).thenAnswer(invocation -> {
			UserSubjectScope value = invocation.getArgument(0);
			value.setId("scope-1");
			return 1;
		});

		UserSubjectScope granted = service.grant(request());

		assertThat(granted.getId()).isEqualTo("scope-1");
		assertThat(granted.getSubjectType()).isEqualTo("MEDIA");
		assertThat(granted.getSubjectId()).isEqualTo("media-1");
	}

	@Test
	void repeatedGrantIsIdempotent() {
		MediaScopeManagementService service = new MediaScopeManagementService(scopeMapper, mediaMapper);
		UserSubjectScope existing = new UserSubjectScope();
		existing.setId("scope-existing");
		when(mediaMapper.selectById("media-1")).thenReturn(media("ACTIVE"));
		when(scopeMapper.countActiveUser("user-1")).thenReturn(1);
		when(scopeMapper.selectOne(any())).thenReturn(existing);

		assertThat(service.grant(request()).getId()).isEqualTo("scope-existing");
		verify(scopeMapper, never()).insert(any(UserSubjectScope.class));
	}

	@Test
	void concurrentDuplicateGrantIsIdempotent() {
		MediaScopeManagementService service = new MediaScopeManagementService(scopeMapper, mediaMapper);
		UserSubjectScope existing = new UserSubjectScope();
		existing.setId("scope-raced");
		when(mediaMapper.selectById("media-1")).thenReturn(media("ACTIVE"));
		when(scopeMapper.countActiveUser("user-1")).thenReturn(1);
		when(scopeMapper.selectOne(any())).thenReturn(null, existing);
		when(scopeMapper.insert(any(UserSubjectScope.class))).thenThrow(new DuplicateKeyException("race"));

		assertThat(service.grant(request()).getId()).isEqualTo("scope-raced");
	}

	@Test
	void rejectsDisabledMediaAndInactiveUser() {
		MediaScopeManagementService service = new MediaScopeManagementService(scopeMapper, mediaMapper);
		when(mediaMapper.selectById("media-1")).thenReturn(media("DISABLED"));
		assertThatThrownBy(() -> service.grant(request())).isInstanceOf(IllegalArgumentException.class);

		when(mediaMapper.selectById("media-1")).thenReturn(media("ACTIVE"));
		when(scopeMapper.countActiveUser("user-1")).thenReturn(0);
		assertThatThrownBy(() -> service.grant(request())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("用户不存在或已停用");
	}

	@Test
	void pageRejectsUnknownOrDisabledMediaWithoutQueryingScopes() {
		MediaScopeManagementService service = new MediaScopeManagementService(scopeMapper, mediaMapper);
		when(mediaMapper.selectById("media-1")).thenReturn(media("DISABLED"));

		assertThatThrownBy(() -> service.page(new Page<>(), "media-1", null))
			.isInstanceOf(IllegalArgumentException.class).hasMessage("资源不存在");
		verify(scopeMapper, never()).selectPage(any(), any());
	}

	@Test
	void revokeCannotCrossMediaBoundary() {
		MediaScopeManagementService service = new MediaScopeManagementService(scopeMapper, mediaMapper);
		UserSubjectScope existing = new UserSubjectScope();
		existing.setId("scope-1");
		existing.setSubjectType("MEDIA");
		existing.setSubjectId("media-other");
		when(scopeMapper.selectById("scope-1")).thenReturn(existing);

		assertThatThrownBy(() -> service.revoke("scope-1", "media-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
		verify(scopeMapper, never()).deleteById("scope-1");
	}

	private MediaScopeGrantRequest request() {
		MediaScopeGrantRequest request = new MediaScopeGrantRequest();
		request.setUserId("user-1");
		request.setMediaId("media-1");
		return request;
	}

	private Media media(String status) {
		Media media = new Media();
		media.setId("media-1");
		media.setStatus(status);
		return media;
	}

}
