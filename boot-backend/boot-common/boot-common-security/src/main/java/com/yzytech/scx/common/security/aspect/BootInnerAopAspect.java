

package com.yzytech.scx.common.security.aspect;

import cn.hutool.core.util.StrUtil;
import com.yzytech.scx.common.core.constant.SecurityConstants;
import com.yzytech.scx.common.security.annotation.BootInner;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 内部接口调用处理 aop
 *
 * @date 2022/6/28
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class BootInnerAopAspect {

	private final HttpServletRequest request;

	@SneakyThrows
	@Around("@annotation(com.yzytech.scx.common.security.annotation.BootInner)")
	public Object around(ProceedingJoinPoint point) {
		// 获取方法签名
		MethodSignature methodSignature = (MethodSignature) point.getSignature();
		// 获取方法
		Method method = methodSignature.getMethod();
		// 获取方法上面的注解
		BootInner hxInner = method.getAnnotation(BootInner.class);
		String header = request.getHeader(SecurityConstants.SOURCE);
		if (hxInner.value() && !StrUtil.equals(SecurityConstants.SOURCE_IN, header)) {
			log.warn("访问接口 {} 没有权限", point.getSignature().getName());
			throw new BootBusinessException("无权访问");
		}
		return point.proceed();
	}

}
