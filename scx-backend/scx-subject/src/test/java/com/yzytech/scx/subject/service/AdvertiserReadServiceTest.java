package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.subject.entity.Advertiser;
import com.yzytech.scx.subject.mapper.AdvertiserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvertiserReadServiceTest {

	@Test
	void nonAdminCanOnlyReadMapperScopedAdvertisers() {
		AdvertiserMapper mapper = mock(AdvertiserMapper.class);
		Advertiser advertiser = new Advertiser();
		advertiser.setId("adv-a");
		when(mapper.findAuthorized("user-a")).thenReturn(List.of(advertiser));
		AdvertiserReadService service = new AdvertiserReadService(mapper);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("user-a", "ROLE_OPERATOR"));
			assertThat(service.listForCurrentUser()).containsExactly(advertiser);
		}

		verify(mapper).findAuthorized("user-a");
		verify(mapper, never()).selectList(any());
	}

	@Test
	void adminCanReadAllActiveAdvertisers() {
		AdvertiserMapper mapper = mock(AdvertiserMapper.class);
		Advertiser advertiser = new Advertiser();
		advertiser.setId("adv-a");
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(advertiser));
		AdvertiserReadService service = new AdvertiserReadService(mapper);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("admin", "ROLE_ADMIN"));
			assertThat(service.listForCurrentUser()).containsExactly(advertiser);
		}

		verify(mapper).selectList(any(Wrapper.class));
		verify(mapper, never()).findAuthorized(anyString());
	}

	private BootUser user(String userId, String role) {
		BootUser user = new BootUser();
		user.setUserId(userId);
		user.setRoles(List.of(role));
		return user;
	}
}
