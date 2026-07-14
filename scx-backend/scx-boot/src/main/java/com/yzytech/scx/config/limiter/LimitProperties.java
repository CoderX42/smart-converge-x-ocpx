package com.yzytech.scx.config.limiter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit.global")
public class LimitProperties {

	/**
	 * 是否启用全局限流
	 */
	private boolean enabled = true;

	/**
	 * 全局最大 QPS
	 */
	private int maxQps = 200;

	/**
	 * 限流窗口（秒）
	 */
	private int windowSeconds = 1;

	/**
	 * 不限流的路径前缀（可选）
	 */
	private List<String> excludePaths = new ArrayList<>();

}
