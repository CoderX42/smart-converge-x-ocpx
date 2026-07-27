package com.yzytech.scx.auth.listener;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.event.BootLogEvent;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseSecurityAuditListenerTest {

    private BaseSecurityAuditListener listener;

    private ApplicationEventPublisher publisher;

    private MockedStatic<RequestContextHolder> requestContextHolderMock;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private HttpServletRequest request;

    private ServletRequestAttributes attributes;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        listener = new BaseSecurityAuditListener(publisher);
        request = mock(HttpServletRequest.class);
        attributes = mock(ServletRequestAttributes.class);

        requestContextHolderMock = mockStatic(RequestContextHolder.class);
        securityUtilsMock = mockStatic(SecurityUtils.class);

        requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        when(attributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        requestContextHolderMock.close();
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("未登录异常应发布 NOT_LOGIN 审计事件")
    void shouldPublishAuditEventOnNotLoginException() {
        // Arrange
        NotLoginException nle = new NotLoginException("未提供token", NotLoginException.NOT_TOKEN, "scene");
        when(request.getRequestURI()).thenReturn("/boot/subjects/media");
        when(request.getMethod()).thenReturn("GET");
        securityUtilsMock.when(SecurityUtils::getUser).thenReturn(null);

        // Act
        listener.afterNotLoginException(nle, Result.fail("test"));

        // Assert
        ArgumentCaptor<BootLogEvent> captor = ArgumentCaptor.forClass(BootLogEvent.class);
        verify(publisher).publishEvent(captor.capture());

        BootLogEvent event = captor.getValue();
        assertThat(event.getSysLog().getTitle()).isEqualTo("无权限访问");
        assertThat(event.getSysLog().getStatus()).isEqualTo("1");
        assertThat(event.getSysLog().getExMsg()).contains("NOT_LOGIN");
        assertThat(event.getSysLog().getCreateId()).isEqualTo("system");
        assertThat(event.getSysLog().getRequestUri()).isEqualTo("/boot/subjects/media");
    }

    @Test
    @DisplayName("无权限异常应发布 NOT_PERMISSION 审计事件并包含操作用户名")
    void shouldPublishAuditEventOnNotPermissionException() {
        // Arrange
        NotPermissionException npe = new NotPermissionException("ad:channel-client:write");
        when(request.getRequestURI()).thenReturn("/boot/ad/channel-clients");
        when(request.getMethod()).thenReturn("GET");
        BootUser user = new BootUser();
        user.setUsername("base02-operator");
        securityUtilsMock.when(SecurityUtils::getUser).thenReturn(user);

        // Act
        listener.afterNotPermissionException(npe, Result.fail("test"));

        // Assert
        ArgumentCaptor<BootLogEvent> captor = ArgumentCaptor.forClass(BootLogEvent.class);
        verify(publisher).publishEvent(captor.capture());

        BootLogEvent event = captor.getValue();
        assertThat(event.getSysLog().getExMsg()).contains("NOT_PERMISSION");
        assertThat(event.getSysLog().getExMsg()).contains("ad:channel-client:write");
        assertThat(event.getSysLog().getUserName()).isEqualTo("base02-operator");
    }

    @Test
    @DisplayName("无角色异常应发布 NOT_ROLE 审计事件并记录请求方法")
    void shouldPublishAuditEventOnNotRoleException() {
        // Arrange
        NotRoleException nre = new NotRoleException("admin");
        when(request.getRequestURI()).thenReturn("/boot/subjects/media");
        when(request.getMethod()).thenReturn("POST");
        BootUser user = new BootUser();
        user.setUsername("base02-analyst");
        securityUtilsMock.when(SecurityUtils::getUser).thenReturn(user);

        // Act
        listener.afterNotRoleException(nre, Result.fail("test"));

        // Assert
        ArgumentCaptor<BootLogEvent> captor = ArgumentCaptor.forClass(BootLogEvent.class);
        verify(publisher).publishEvent(captor.capture());

        BootLogEvent event = captor.getValue();
        assertThat(event.getSysLog().getExMsg()).contains("NOT_ROLE");
        assertThat(event.getSysLog().getExMsg()).contains("无此角色：admin");
        assertThat(event.getSysLog().getRequestMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("跨媒体权限越界访问应发布审计事件并记录被拒绝的资源 URI")
    void shouldPublishAuditForCrossMediaAccessDenial() {
        // Arrange
        NotPermissionException npe = new NotPermissionException("subject:media:read");
        when(request.getRequestURI()).thenReturn("/boot/subjects/media/media_other");
        when(request.getMethod()).thenReturn("GET");
        BootUser user = new BootUser();
        user.setUsername("base02-operator");
        securityUtilsMock.when(SecurityUtils::getUser).thenReturn(user);

        // Act
        listener.afterNotPermissionException(npe, Result.fail("test"));

        // Assert
        ArgumentCaptor<BootLogEvent> captor = ArgumentCaptor.forClass(BootLogEvent.class);
        verify(publisher).publishEvent(captor.capture());

        BootLogEvent event = captor.getValue();
        assertThat(event.getSysLog().getRequestUri()).contains("media_other");
        assertThat(event.getSysLog().getTitle()).isEqualTo("无权限访问");
    }

    @Test
    @DisplayName("RequestContext 为 null 时不应发布事件也不抛异常")
    void shouldNotThrowWhenRequestContextIsNull() {
        // Arrange
        NotPermissionException npe = new NotPermissionException("subject:media:read");
        requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

        // Act & Assert
        assertThatCode(() -> listener.afterNotPermissionException(npe, Result.fail("test")))
                .doesNotThrowAnyException();
        verify(publisher, never()).publishEvent(any());
    }

}
