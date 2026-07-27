package com.yzytech.scx.auth.listener;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.yzytech.scx.common.core.util.IpUtils;
import com.yzytech.scx.common.log.event.BootLogEvent;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 安全审计监听器：拦截全局异常处理器中的权限异常，发布审计事件到 sys_log 表
 *
 * @date 2026/7/27
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class BaseSecurityAuditListener {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 拦截未登录异常，发布审计事件
     */
    @AfterReturning(pointcut = "execution(* com.yzytech.scx.common.security.handler.BootGlobalExceptionHandlerResolver.handlerNotLoginException(..)) && args(nle)", returning = "result")
    public void afterNotLoginException(NotLoginException nle, Object result) {
        publishAudit("NOT_LOGIN", nle.getMessage(), nle);
    }

    /**
     * 拦截无权限异常，发布审计事件
     */
    @AfterReturning(pointcut = "execution(* com.yzytech.scx.common.security.handler.BootGlobalExceptionHandlerResolver.handleNotPermissionException(..)) && args(e)", returning = "result")
    public void afterNotPermissionException(Exception e, Object result) {
        publishAudit("NOT_PERMISSION", e.getMessage(), e);
    }

    /**
     * 拦截无角色异常，发布审计事件
     */
    @AfterReturning(pointcut = "execution(* com.yzytech.scx.common.security.handler.BootGlobalExceptionHandlerResolver.handleNotRoleException(..)) && args(e)", returning = "result")
    public void afterNotRoleException(NotRoleException e, Object result) {
        publishAudit("NOT_ROLE", e.getMessage(), e);
    }

    /**
     * 发布审计事件到 sys_log 表（内部异常不影响正常响应）
     *
     * @param reasonCode 异常原因编码
     * @param message    异常消息
     * @param e          异常对象
     */
    private void publishAudit(String reasonCode, String message, Throwable e) {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();

            // 获取当前用户信息（可能为 null）
            String userName = null;
            try {
                BootUser user = SecurityUtils.getUser();
                if (user != null) {
                    userName = user.getUsername();
                }
            } catch (Exception ex) {
                // 当前请求未登录或获取失败，不阻塞审计
            }

            String ipAddr = JakartaServletUtil.getClientIP(request);

            // 构建异常消息，限制长度避免过载
            String exMsg = reasonCode + ": " + message;
            if (exMsg.length() > 2000) {
                exMsg = exMsg.substring(0, 2000);
            }

            SysLogReqDTO dto = new SysLogReqDTO();
            dto.setTitle("无权限访问");
            dto.setStatus("1");
            dto.setUserName(userName);
            dto.setCreateId("system");
            dto.setRequestUri(request.getRequestURI());
            dto.setRequestMethod(request.getMethod());
            dto.setMethod(e.getClass().getSimpleName());
            dto.setIpAddr(ipAddr);
            dto.setLocation(IpUtils.getWhoisAddress(ipAddr));
            dto.setExMsg(exMsg);
            dto.setRequestParams(request.getQueryString());
            dto.setRequestTime(0L);

            applicationEventPublisher.publishEvent(new BootLogEvent(this, dto));
        } catch (Exception ex) {
            log.error("Failed to publish security audit event", ex);
        }
    }
}
