/**
 * Copyright (c) 2025 江苏瑶知云科技有限公司
 * All rights reserved.
 */

package com.yzytech.scx.common.core.sign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 开放接口请求头签名校验（X-Timestamp、X-Nonce、X-Sign）。 配置在 {@code application.yml} 的 {@code api.sign}
 * 下；单体与网关共用同一前缀。
 *
 * 经网关转发时：若网关已启用 {@link #replayProtection} 且使用 Redis 记录 nonce，下游服务建议关闭防重放， 否则网关已消费的 nonce
 * 会导致下游验签失败。
 */
@Data
@ConfigurationProperties(prefix = "api.sign")
public class ApiSignProperties {

	/**
	 * 是否启用（默认关闭）
	 */
	private boolean enabled = false;

	/**
	 * 与前端一致的密钥
	 */
	private String secret = "";

	/**
	 * 请求最大有效期（毫秒）
	 */
	private long expireMs = 60_000L;

	/**
	 * 时钟偏差容忍（毫秒）
	 */
	private long clockSkewMs = 30_000L;

	/**
	 * 是否使用 Redis 记录 nonce 防重放
	 */
	private boolean replayProtection = true;

	/**
	 * 排除路径（Ant），不验签
	 */
	private List<String> excludePatterns = new ArrayList<>();

}
