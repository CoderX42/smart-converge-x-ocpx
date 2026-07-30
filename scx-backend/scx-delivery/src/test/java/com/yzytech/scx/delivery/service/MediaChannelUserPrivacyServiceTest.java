package com.yzytech.scx.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.delivery.entity.MediaChannelUser;
import com.yzytech.scx.delivery.mapper.MediaChannelMapper;
import com.yzytech.scx.delivery.mapper.MediaChannelUserMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import org.junit.jupiter.api.Test;

class MediaChannelUserPrivacyServiceTest {

	private final MediaChannelUserMapper mapper = mock(MediaChannelUserMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final MediaChannelUserService service = new MediaChannelUserService(mapper, mock(MediaChannelMapper.class),
			scopeService);

	@Test
	void returnsSensitiveDetailsOnlyInsideReadableMediaScope() {
		MediaChannelUser user = user();
		when(scopeService.canRead("media-1")).thenReturn(true);
		when(mapper.selectById("user-1")).thenReturn(user);

		assertThat(service.sensitive("user-1", "media-1").phone()).isEqualTo("13800138000");
	}

	@Test
	void hidesExistenceWhenRequestedMediaDoesNotMatch() {
		when(scopeService.canRead("media-2")).thenReturn(true);
		when(mapper.selectById("user-1")).thenReturn(user());

		assertThatThrownBy(() -> service.sensitive("user-1", "media-2"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("资源不存在");
	}

	private MediaChannelUser user() {
		MediaChannelUser user = new MediaChannelUser();
		user.setId("user-1");
		user.setMediaId("media-1");
		user.setPhone("13800138000");
		user.setEmail("operator@example.com");
		return user;
	}
}
