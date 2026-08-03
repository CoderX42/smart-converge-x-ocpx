package com.yzytech.scx.adx.service;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdxAccessScopeTest {

	private final AdxAccessScope scope = new AdxAccessScope();

	@Test
	void advertiserCanOnlyUseItsBoundAdvertiserId() {
		BootUser user = new BootUser();
		user.setType("2");
		user.setShopId("advertiser-1");
		try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user);

			assertThat(scope.constrainAdvertiser("advertiser-2")).isEqualTo("advertiser-1");
		}
	}

	@Test
	void advertiserWithoutBindingFailsClosed() {
		BootUser user = new BootUser();
		user.setType("2");
		try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user);

			assertThatThrownBy(scope::currentAdvertiserId).isInstanceOf(IllegalStateException.class)
				.hasMessage("广告主账号未绑定广告主");
		}
	}

}
