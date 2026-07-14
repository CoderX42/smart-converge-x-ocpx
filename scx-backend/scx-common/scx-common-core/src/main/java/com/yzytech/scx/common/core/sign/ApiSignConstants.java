/**
 * Copyright (c) 2025 江苏瑶知云科技有限公司
 * All rights reserved.
 */

package com.yzytech.scx.common.core.sign;

/**
 * 开放接口签名校验请求头与 Redis 前缀（单体与网关共用）。
 */
public final class ApiSignConstants {

	private ApiSignConstants() {
	}

	public static final String HEADER_TIMESTAMP = "X-Timestamp";

	public static final String HEADER_NONCE = "X-Nonce";

	public static final String HEADER_SIGN = "X-Sign";

	public static final String NONCE_KEY_PREFIX = "api:sign:nonce:";

}
