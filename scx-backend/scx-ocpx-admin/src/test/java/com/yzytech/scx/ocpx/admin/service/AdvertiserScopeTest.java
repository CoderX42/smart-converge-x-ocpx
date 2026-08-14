package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class AdvertiserScopeTest {

	private final AdvertiserScope scope = new AdvertiserScope();

	@Test
	void administratorKeepsRequestedAdvertiserFilter() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("0", null));

			assertThat(scope.constrain(" requested-1 ")).isEqualTo("requested-1");
		}
	}

	@Test
	void advertiserAccountOverridesForgedFilterWithBoundShopId() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("2", "advertiser-own"));

			assertThat(scope.constrain("advertiser-other")).isEqualTo("advertiser-own");
		}
	}

	@Test
	void advertiserAccountWithoutShopIdFailsClosed() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("2", " "));

			assertThatThrownBy(() -> scope.constrain(null)).isInstanceOfSatisfying(BootBusinessException.class,
					error -> assertThat(error.getCode()).isEqualTo(403));
		}
	}

	@Test
	void advertiserAccountCannotWriteAnotherAdvertiser() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("2", "advertiser-own"));

			assertThatThrownBy(() -> scope.requireOwner("advertiser-other"))
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
		}
	}

	private BootUser user(String type, String shopId) {
		BootUser user = new BootUser();
		user.setType(type);
		user.setShopId(shopId);
		return user;
	}

}
