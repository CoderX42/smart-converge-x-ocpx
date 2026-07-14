

package com.yzytech.scx.system.handler;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社交登录策略工厂 通过 Spring 自动收集所有策略，按类型管理
 *
 */
@Component
public class SocialLoginFactory {

	private final Map<String, SocialLoginStrategy> strategyMap = new HashMap<>();

	/**
	 * 构造方法中注入所有策略实现类
	 * @param strategies Spring 会自动注入所有实现 SocialLoginStrategy 的 Bean
	 */
	public SocialLoginFactory(List<SocialLoginStrategy> strategies) {
		for (SocialLoginStrategy strategy : strategies) {
			strategyMap.put(strategy.getType(), strategy);
		}
	}

	/**
	 * 根据类型获取对应的策略
	 * @param type 登录类型（如 1=网页微信，2=小程序）
	 * @return 对应的策略实现
	 */
	public SocialLoginStrategy getStrategy(String type) {
		return strategyMap.get(type);
	}

}
