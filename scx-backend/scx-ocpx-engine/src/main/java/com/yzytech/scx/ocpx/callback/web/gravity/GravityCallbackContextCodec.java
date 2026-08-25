package com.yzytech.scx.ocpx.callback.web.gravity;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** V121 密钥环保护引力 Callback，并兼容 V120 无 kid 历史上下文。 */
@Component
public class GravityCallbackContextCodec {

	private static final int MAX_TOKEN_LENGTH = 12000;

	private final OcpxKeyRingCrypto crypto;

	@Autowired
	public GravityCallbackContextCodec(OcpxKeyRingCrypto crypto) {
		this.crypto = crypto;
	}

	/** 测试兼容构造器。 */
	public GravityCallbackContextCodec(String callbackKey) {
		this(new OcpxKeyRingCrypto("test", java.util.Map.of("test", callbackKey), java.util.List.of("test")));
	}

	public String activeKeyId() {
		return crypto.activeKeyId();
	}

	public String encode(GravityCallbackContext context) {
		if (context == null) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
		return crypto.encryptCallback(JSON.toJSONString(context));
	}

	public GravityCallbackContext decode(String token, String keyId) {
		if (!StringUtils.hasText(token) || token.length() > MAX_TOKEN_LENGTH) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
		try {
			byte[] clear = StringUtils.hasText(keyId)
					? crypto.decryptCallback(keyId, token).getBytes(java.nio.charset.StandardCharsets.UTF_8)
					: crypto.decryptLegacyGravity(token);
			return JSON.parseObject(clear, GravityCallbackContext.class);
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID", exception);
		}
	}

	/** 新格式测试默认使用活动 key；生产请求始终显式传 kid。 */
	public GravityCallbackContext decode(String token) {
		return decode(token, activeKeyId());
	}

	/** 无 kid 入口先兼容 V120 密文，再兼容短暂存在的新格式活动密钥链接。 */
	public GravityCallbackContext decodeCompatible(String token) {
		if (!StringUtils.hasText(token) || token.length() > MAX_TOKEN_LENGTH) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
		try {
			return JSON.parseObject(crypto.decryptLegacyGravity(token), GravityCallbackContext.class);
		}
		catch (RuntimeException legacyException) {
			try {
				return JSON.parseObject(crypto.decryptCallback(activeKeyId(), token), GravityCallbackContext.class);
			}
			catch (RuntimeException currentException) {
				currentException.addSuppressed(legacyException);
				throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID", currentException);
			}
		}
	}

}
