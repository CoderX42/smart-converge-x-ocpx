package com.yzytech.scx.common.security.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.yzytech.scx.common.security.listener.BaseSecurityAuditListener;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BootGlobalExceptionHandlerResolverTest {

	@Test
	void recordsEverySaTokenDenialCategoryWithoutChangingTheHttpResponsePath() {
		BaseSecurityAuditListener auditListener = mock(BaseSecurityAuditListener.class);
		BootGlobalExceptionHandlerResolver resolver = new BootGlobalExceptionHandlerResolver(auditListener);

		MockHttpServletRequest crossMediaRead = new MockHttpServletRequest("GET", "/boot/subjects/media/media_other");
		resolver.handleNotPermissionException(new NotPermissionException("subject:media:read"), crossMediaRead);
		verify(auditListener).record(crossMediaRead, "NOT_PERMISSION");

		MockHttpServletRequest crossMediaWrite = new MockHttpServletRequest("POST", "/boot/ad/channel-clients");
		resolver.handleNotPermissionException(new NotPermissionException("ad:channel-client:write"), crossMediaWrite);
		verify(auditListener).record(crossMediaWrite, "NOT_PERMISSION");

		MockHttpServletRequest adminWrite = new MockHttpServletRequest("POST", "/boot/system/user");
		resolver.handleNotPermissionException(new NotPermissionException("system:sysuser:add"), adminWrite);
		verify(auditListener).record(adminWrite, "NOT_PERMISSION");

		MockHttpServletRequest roleRequest = new MockHttpServletRequest("POST", "/boot/ad/channel-clients");
		resolver.handleNotRoleException(new NotRoleException("ROLE_OPERATOR"), roleRequest);
		verify(auditListener).record(roleRequest, "NOT_ROLE");

		MockHttpServletRequest loginRequest = new MockHttpServletRequest("GET", "/boot/subjects/media");
		resolver.handlerNotLoginException(
				NotLoginException.newInstance(NotLoginException.NOT_TOKEN, "未提供token", null, "login"), loginRequest);
		verify(auditListener).record(loginRequest, "NOT_LOGIN");
	}

}
