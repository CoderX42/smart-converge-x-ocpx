package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import com.yzytech.scx.common.core.constant.CommonConstants;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

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
	void advertiserAccountWithExplicitAllScopeKeepsRequestedFilterAndPassesGlobalGuard() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			BootUser user = user("2", "advertiser-own");
			user.setPermissions(List.of(SystemAccountConstants.ADVERTISER_ALL_SCOPE_PERMISSION));
			security.when(SecurityUtils::getUser).thenReturn(user);

			assertThat(scope.constrain("advertiser-other")).isEqualTo("advertiser-other");
			scope.requireAdministrator();
			scope.requireOwner("advertiser-other");
		}
	}

	@Test
	void allScopeNeverExpandsStrictCreativeOwnership() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			BootUser user = user("2", "advertiser-own");
			user.setPermissions(List.of(SystemAccountConstants.ADVERTISER_ALL_SCOPE_PERMISSION));
			security.when(SecurityUtils::getUser).thenReturn(user);

			assertThat(scope.constrainSelfOwned("advertiser-own")).isEqualTo("advertiser-own");
			assertThatThrownBy(() -> scope.constrainSelfOwned("advertiser-other"))
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
			assertThatThrownBy(() -> scope.requireSelfOwnedAdvertiser("advertiser-other"))
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
		}
	}

	@Test
	void internalOperatorWithDedicatedPermissionCanChangeBillingWithoutAdminRole() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			BootUser operator = user("0", null);
			operator.setRoles(List.of("ROLE_OPERATOR"));
			operator.setPermissions(List.of(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION));
			security.when(SecurityUtils::getUser).thenReturn(operator);

			scope.requireInternalPermission(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION);
		}
	}

	@Test
	void advertiserCannotChangeBillingEvenWhenPermissionWasMisgranted() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			BootUser advertiser = user("2", "advertiser-own");
			advertiser.setPermissions(List.of(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION));
			security.when(SecurityUtils::getUser).thenReturn(advertiser);

			assertThatThrownBy(
					() -> scope.requireInternalPermission(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION))
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
		}
	}

	@Test
	void allScopeAdvertiserStillSeesOnlyItsOwnAccountAndCannotMaintainBilling() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			BootUser user = user("2", "advertiser-own");
			user.setPermissions(List.of(SystemAccountConstants.ADVERTISER_ALL_SCOPE_PERMISSION));
			user.setRoles(List.of(CommonConstants.ROLE_ADMIN_CODE));
			security.when(SecurityUtils::getUser).thenReturn(user);

			assertThat(scope.accountAdvertiserId("advertiser-other")).isEqualTo("advertiser-own");
			assertThatThrownBy(scope::requireSystemAdministrator)
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
		}
	}

	@Test
	void onlyRealRoleAdminPassesTheFinanceGuard() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			BootUser user = user("0", null);
			user.setRoles(List.of(CommonConstants.ROLE_ADMIN_CODE));
			user.setPermissions(List.of(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION));
			security.when(SecurityUtils::getUser).thenReturn(user);

			scope.requireSystemAdministrator();
			scope.requireSystemAdministratorPermission(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION);
		}
	}

	@Test
	void roleAdminWithoutTheExplicitBillingPermissionCannotChangeBillingConfiguration() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			BootUser user = user("0", null);
			user.setRoles(List.of(CommonConstants.ROLE_ADMIN_CODE));
			user.setPermissions(List.of("ocpx:monitor-task:edit"));
			security.when(SecurityUtils::getUser).thenReturn(user);

			assertThatThrownBy(() -> scope.requireSystemAdministratorPermission(
					SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION))
				.isInstanceOfSatisfying(BootBusinessException.class,
						error -> assertThat(error.getCode()).isEqualTo(403));
		}
	}

	@Test
	void missingSessionAndOrdinarySystemUsersFailTheFinanceGuard() {
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(null);
			assertThatThrownBy(scope::requireSystemAdministrator).isInstanceOf(BootBusinessException.class);

			BootUser user = user("0", null);
			user.setRoles(List.of("ROLE_OPERATOR"));
			security.when(SecurityUtils::getUser).thenReturn(user);
			assertThatThrownBy(scope::requireSystemAdministrator).isInstanceOf(BootBusinessException.class);
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
