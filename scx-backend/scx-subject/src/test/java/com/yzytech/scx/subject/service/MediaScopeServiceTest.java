package com.yzytech.scx.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MediaScopeServiceTest {

	private final UserSubjectScopeMapper scopeMapper = mock(UserSubjectScopeMapper.class);

	private final MediaScopeService service = new MediaScopeService(scopeMapper);

	private MockedStatic<SecurityUtils> security;

	@BeforeEach
	void setUp() {
		security = mockStatic(SecurityUtils.class);
	}

	@AfterEach
	void tearDown() {
		security.close();
	}

	@Test
	void adminReadsEveryMedia() {
		BootUser admin = new BootUser();
		admin.setRoles(List.of(CommonConstants.ROLE_ADMIN_CODE));
		security.when(SecurityUtils::getUser).thenReturn(admin);

		assertThat(service.isAdmin()).isTrue();
		assertThat(service.canRead("media-any")).isTrue();
		assertThat(service.canWrite("media-any")).isTrue();
	}

	@Test
	void operatorWithoutMediaScopeIsBlocked() {
		BootUser operator = new BootUser();
		operator.setUserId("op-1");
		operator.setRoles(List.of("ROLE_OPERATOR"));
		security.when(SecurityUtils::getUser).thenReturn(operator);
		when(scopeMapper.selectList(any())).thenReturn(Collections.emptyList());

		assertThat(service.isAdmin()).isFalse();
		assertThat(service.canRead("media_sandbox")).isFalse();
		assertThat(service.canWrite("media_sandbox")).isFalse();
	}

	@Test
	void operatorWithScopeCanReadAndWrite() {
		BootUser operator = new BootUser();
		operator.setUserId("op-2");
		operator.setRoles(List.of("ROLE_OPERATOR"));
		security.when(SecurityUtils::getUser).thenReturn(operator);
		UserSubjectScope scope = new UserSubjectScope();
		scope.setSubjectId("media_sandbox");
		when(scopeMapper.selectList(any())).thenReturn(List.of(scope));

		assertThat(service.canRead("media_sandbox")).isTrue();
		assertThat(service.canWrite("media_sandbox")).isTrue();
		assertThat(service.canRead("media_other")).isFalse();
	}

	@Test
	void nonOperatorCannotWriteEvenWithScope() {
		BootUser analyst = new BootUser();
		analyst.setUserId("a-1");
		analyst.setRoles(List.of("ROLE_ANALYST"));
		security.when(SecurityUtils::getUser).thenReturn(analyst);
		UserSubjectScope scope = new UserSubjectScope();
		scope.setSubjectId("media_sandbox");
		when(scopeMapper.selectList(any())).thenReturn(List.of(scope));

		assertThat(service.canRead("media_sandbox")).isTrue();
		assertThat(service.canWrite("media_sandbox")).isFalse();
	}

	@Test
	void unauthenticatedUserHasNoAccess() {
		security.when(SecurityUtils::getUser).thenReturn(null);

		assertThat(service.isAdmin()).isFalse();
		assertThat(service.canRead("media-any")).isFalse();
		assertThat(service.canWrite("media-any")).isFalse();
	}

	@Test
	void analystWithScopeCannotEnumerateUnauthorizedMedia() {
		BootUser analyst = new BootUser();
		analyst.setUserId("a-2");
		analyst.setRoles(List.of("ROLE_ANALYST"));
		security.when(SecurityUtils::getUser).thenReturn(analyst);
		UserSubjectScope scope = new UserSubjectScope();
		scope.setSubjectId("media_sandbox");
		when(scopeMapper.selectList(any())).thenReturn(List.of(scope));

		assertThat(service.readableMediaIds()).containsExactly("media_sandbox");
		assertThat(service.canRead("media_sandbox")).isTrue();
		assertThat(service.canRead("media_other")).isFalse();
		assertThat(service.canWrite("media_other")).isFalse();
	}

	@Test
	void readableMediaIdsExcludesUnauthorisedAndUnauthenticated() {
		BootUser op = new BootUser();
		op.setUserId("op-3");
		op.setRoles(List.of("ROLE_OPERATOR"));
		security.when(SecurityUtils::getUser).thenReturn(op);
		when(scopeMapper.selectList(any())).thenReturn(Collections.emptyList());

		assertThat(service.readableMediaIds()).isEmpty();
		assertThat(service.canRead("media_sandbox")).isFalse();

		security.when(SecurityUtils::getUser).thenReturn(null);
		assertThat(service.readableMediaIds()).isEmpty();
	}

}
