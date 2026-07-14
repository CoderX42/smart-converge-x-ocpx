

package com.yzytech.scx.common.idempotent.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.idempotent.annotation.Idempotent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * @description 幂等切面
 * @date 2024/12/26
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

	private final RedissonClient redissonClient;

	private final SpelExpressionParser parser = new SpelExpressionParser();

	private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

	private final static String KEY_PREFIX = "idempotent";

	private final ObjectMapper objectMapper;

	@Around("@annotation(idempotent)")
	public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
		// 解析方法签名
		Object[] args = joinPoint.getArgs();
		String idempotentKey = "";
		// 解析 SpEL 表达式
		String spelKey = idempotent.key();
		if (StringUtils.hasText(spelKey)) {
			MethodSignature signature = (MethodSignature) joinPoint.getSignature();
			Method method = signature.getMethod();
			String[] paramNames = nameDiscoverer.getParameterNames(method);

			// 创建上下文绑定参数
			EvaluationContext context = new StandardEvaluationContext();
			for (int i = 0; i < paramNames.length; i++) {
				context.setVariable(paramNames[i], args[i]);
			}
			idempotentKey = parser.parseExpression(spelKey).getValue(context, String.class);
			if (StringUtils.isEmpty(idempotentKey)) {
				throw new RuntimeException("幂等 Key 解析为空，请检查 SpEL 表达式");
			}
		}
		else {
			// key 为空：使用 URL + 参数摘要
			HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
			String url = request.getRequestURI();
			String json = objectMapper.writeValueAsString(joinPoint.getArgs());
			String key = DigestUtils.md5DigestAsHex(json.getBytes(StandardCharsets.UTF_8));
			idempotentKey = url + key;
		}

		// 使用 Redisson 的 MapCache 实现幂等控制
		RMapCache<String, String> mapCache = redissonClient.getMapCache(KEY_PREFIX);
		String oldValue = mapCache.putIfAbsent(idempotentKey, "1", idempotent.expire(), TimeUnit.SECONDS);
		if (oldValue != null) {
			throw new RuntimeException(idempotent.message());
		}

		try {
			return joinPoint.proceed();
		}
		finally {
			// 是否删除由业务决定，通常交由 TTL 过期控制即可
			if (idempotent.delKey()) {
				mapCache.remove(idempotentKey);
			}
		}
	}

}
