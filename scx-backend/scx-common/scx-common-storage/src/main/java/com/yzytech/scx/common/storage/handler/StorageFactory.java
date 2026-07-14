

package com.yzytech.scx.common.storage.handler;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
@Component
public class StorageFactory {

	private final Map<String, BootUploadFileHandler> strategyMap = new HashMap<>();

	/**
	 * 构造方法中注入所有策略实现类
	 * @param strategies Spring 会自动注入所有实现 SocialLoginStrategy 的 Bean
	 */
	public StorageFactory(List<BootUploadFileHandler> strategies) {
		for (BootUploadFileHandler strategy : strategies) {
			strategyMap.put(strategy.getType(), strategy);
		}
	}

	/**
	 * 根据类型获取对应的策略
	 * @param type 存储类型
	 * @return 对应的策略实现
	 */
	public BootUploadFileHandler getStrategy(String type) {
		return strategyMap.get(type);
	}

}
