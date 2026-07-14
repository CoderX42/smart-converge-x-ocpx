/**
 * Copyright (c) 2025 江苏瑶知云科技有限公司
 * All rights reserved.
 */

package com.yzytech.scx.config.sign;

import com.yzytech.scx.common.core.sign.ApiSignProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 启用后对未排除路径校验签名（与网关规则一致，配置前缀 {@code api.sign}）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ApiSignProperties.class)
@ConditionalOnProperty(prefix = "api.sign", name = "enabled", havingValue = "true")
public class ApiSignConfiguration implements WebMvcConfigurer {

	private final ApiSignProperties properties;

	private final ApiSignInterceptor apiSignInterceptor;

	public ApiSignConfiguration(ApiSignProperties properties, ObjectProvider<StringRedisTemplate> stringRedisTemplate) {
		this.properties = properties;
		this.apiSignInterceptor = new ApiSignInterceptor(properties, stringRedisTemplate);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(apiSignInterceptor)
			.addPathPatterns("/**")
			.excludePathPatterns(CollectionUtils.isEmpty(properties.getExcludePatterns()) ? new String[0]
					: properties.getExcludePatterns().toArray(new String[0]))
			.order(Ordered.HIGHEST_PRECEDENCE + 10);
	}

}
