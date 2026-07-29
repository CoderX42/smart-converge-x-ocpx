package com.yzytech.scx.commercial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.commercial.mapper.OrganizationMapper;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrganizationScopeServiceTest {

	private final UserSubjectScopeMapper scopeMapper = mock(UserSubjectScopeMapper.class);

	private final OrganizationMapper organizationMapper = mock(OrganizationMapper.class);

	private final OrganizationScopeService service = new OrganizationScopeService(scopeMapper, organizationMapper);

	@Test
	void adminCanReadAndWriteEveryOrganization() {
		BootUser admin = user("admin", CommonConstants.ROLE_ADMIN_CODE);
		try (var security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(admin);
			assertThat(service.canRead("org-any")).isTrue();
			assertThat(service.canWrite("org-any")).isTrue();
		}
	}

	@Test
	void analystCanReadOnlyGrantedOrganization() {
		BootUser analyst = user("analyst", "ROLE_ANALYST");
		UserSubjectScope scope = new UserSubjectScope();
		scope.setSubjectId("org-1");
		when(scopeMapper.selectList(any())).thenReturn(List.of(scope));
		try (var security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(analyst);
			assertThat(service.canRead("org-1")).isTrue();
			assertThat(service.canRead("org-2")).isFalse();
			assertThat(service.canWrite("org-1")).isFalse();
		}
	}

	@Test
	void operatorCanWriteOnlyGrantedOrganization() {
		BootUser operator = user("operator", "ROLE_OPERATOR");
		UserSubjectScope scope = new UserSubjectScope();
		scope.setSubjectId("org-1");
		when(scopeMapper.selectList(any())).thenReturn(List.of(scope));
		try (var security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(operator);
			assertThat(service.canWrite("org-1")).isTrue();
			assertThat(service.canWrite("org-2")).isFalse();
		}
	}

	private BootUser user(String id, String role) {
		BootUser user = new BootUser();
		user.setUserId(id);
		user.setRoles(List.of(role));
		return user;
	}

}
