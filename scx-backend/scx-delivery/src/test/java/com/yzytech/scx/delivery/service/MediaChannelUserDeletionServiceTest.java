package com.yzytech.scx.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.delivery.entity.MediaChannelUser;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelUserMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

class MediaChannelUserDeletionServiceTest {

	private final MediaChannelUserMapper mapper = mock(MediaChannelUserMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final MediaChannelUserService service = new MediaChannelUserService(mapper, mock(MediaChannelMapper.class),
			scopeService);

	@Test
	void deletesChannelUserOnlyInsideWritableMediaScopeAtExpectedVersion() {
		MediaChannelUser user = user();
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(mapper.selectById("user-1")).thenReturn(user);
		when(mapper.delete(any())).thenReturn(1);

		assertThat(service.delete("user-1", "media-1", 3)).isTrue();
	}

	@Test
	void rejectsStaleChannelUserDeletion() {
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(mapper.selectById("user-1")).thenReturn(user());

		assertThatThrownBy(() -> service.delete("user-1", "media-1", 2)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("刷新后重试");
	}

	private MediaChannelUser user() {
		MediaChannelUser user = new MediaChannelUser();
		user.setId("user-1");
		user.setMediaId("media-1");
		user.setVersion(3);
		return user;
	}

}
