

package com.yzytech.scx.config;

import cn.dev33.satoken.fun.strategy.SaCorsHandleFunction;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * [Sa-Token 权限认证] 配置类
 *
 * @date 2022/6/28
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenConfigure implements WebMvcConfigurer {

	private final PermitAllUrlProperties permitAllUrlProperties;

	/**
	 * 注册Sa-Token的注解拦截器，打开注解式鉴权功能
	 * @param registry
	 * @date 2022/7/16
	 * @return: void
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		List<String> ignoreUrls = permitAllUrlProperties.getUrls();
		log.info("SCX Sa-Token permit-all URLs loaded: {}", ignoreUrls);
		// 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验
		registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
			.addPathPatterns("/**")
			.excludePathPatterns(ignoreUrls.toArray(new String[0]))
			.order(Ordered.HIGHEST_PRECEDENCE + 20);
	}

	/**
	 * CORS 跨域处理策略
	 */
	@Bean
	public SaCorsHandleFunction corsHandle() {
		return (req, res, sto) -> {
			res.
			// 允许指定域访问跨域资源
				setHeader("Access-Control-Allow-Origin", "*")
				// 允许所有请求方式
				.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
				// 有效时间
				.setHeader("Access-Control-Max-Age", "3600")
				// 允许的header参数
				.setHeader("Access-Control-Allow-Headers", "*");

			// 如果是预检请求，则立即返回到前端
			SaRouter.match(SaHttpMethod.OPTIONS).free(r -> System.out.println("--------OPTIONS预检请求，不做处理")).back();
		};
	}

}
