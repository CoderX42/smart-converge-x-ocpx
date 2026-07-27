

package com.yzytech.scx.common.log.aop;

import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.core.util.IpUtils;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.common.log.annotation.SysLog;
import com.yzytech.scx.common.log.event.BootLogEvent;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * AOP实现日志
 *
 * @since 2022/2/17 9:27
 */
@Aspect
@Slf4j
@RequiredArgsConstructor
public class LogAopAspect {

	private final ApplicationEventPublisher applicationEventPublisher;

	/**
	 * 环绕通知记录日志通过注解匹配到需要增加日志功能的方法
	 *
	 * @since 2022/2/17 9:28
	 */
	@SneakyThrows
	@Around("@annotation(sysLog)")
	public Object around(ProceedingJoinPoint point, com.yzytech.scx.common.log.annotation.SysLog sysLog) {
		HttpServletRequest request = ((ServletRequestAttributes) Objects
			.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
		// 1.方法执行前的处理，相当于前置通知
		// 获取方法签名
		MethodSignature methodSignature = (MethodSignature) point.getSignature();
		// 获取方法
		Method method = methodSignature.getMethod();
		// 获取方法上面的注解
		SysLog log = method.getAnnotation(SysLog.class);
		// 获取操作描述的属性值
		String title = log.value();
		// 记录日志
		SysLogReqDTO sysLogReq = new SysLogReqDTO();
		String ip = JakartaServletUtil.getClientIP(request);
		sysLogReq.setMethod(request.getMethod());
		sysLogReq.setIpAddr(ip);
		sysLogReq.setLocation(IpUtils.getWhoisAddress(ip));
		sysLogReq.setRequestMethod(point.getSignature().getDeclaringTypeName() + "." + point.getSignature().getName());
		sysLogReq.setRequestUri(URLUtil.getPath(request.getRequestURI()));
		sysLogReq.setTitle(title);
		sysLogReq.setStatus(CommonConstants.LOGIN_LOG_STATUS_1);
		sysLogReq.setUserName(SecurityUtils.getUser().getUsername());

		// 让代理方法执行
		Long startTime = System.currentTimeMillis();
		Object result = null;
		try {
			result = point.proceed();
			markBusinessResultFailure(sysLogReq, result);
		}
		catch (Exception e) {
			sysLogReq.setStatus(CommonConstants.LOGIN_LOG_STATUS_0);
			sysLogReq.setExMsg(e.getMessage());
			throw e;
		}
		finally {
			Long endTime = System.currentTimeMillis();
			sysLogReq.setRequestTime(endTime - startTime);
			// 异步保存操作日志
			applicationEventPublisher.publishEvent(new BootLogEvent(this, sysLogReq));

		}
		return result;
	}

	static void markBusinessResultFailure(SysLogReqDTO sysLogReq, Object result) {
		if (result instanceof Result<?> apiResult && apiResult.getCode() != CommonConstants.SUCCESS) {
			sysLogReq.setStatus(CommonConstants.LOGIN_LOG_STATUS_0);
			// Do not persist the response message: it may include object identifiers or
			// other request-derived values. The stable business code is enough to
			// distinguish a rejected operation from an exception.
			sysLogReq.setExMsg("BUSINESS_RESULT:" + apiResult.getCode());
		}
	}

}
