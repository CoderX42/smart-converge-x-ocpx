package com.yzytech.scx.ocpx.callback.web.gravity;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用 AES-GCM 保护引力 Callback 路由上下文的机密性与完整性。 */
@Component
public class GravityCallbackContextCodec {

	private static final byte TOKEN_VERSION = 1;

	private static final int IV_LENGTH = 12;

	private static final int TAG_LENGTH_BITS = 128;

	private static final int MAX_TOKEN_LENGTH = 12000;

	private final SecureRandom secureRandom = new SecureRandom();

	private final SecretKeySpec key;

	public GravityCallbackContextCodec(@Value("${app.callback-aes-key}") String callbackKey) {
		if (!StringUtils.hasText(callbackKey)) {
			throw new IllegalArgumentException("callback key is required");
		}
		try {
			byte[] derived = MessageDigest.getInstance("SHA-256").digest(callbackKey.getBytes(StandardCharsets.UTF_8));
			this.key = new SecretKeySpec(derived, "AES");
		}
		catch (Exception exception) {
			throw new IllegalStateException("unable to initialize callback context key", exception);
		}
	}

	public String encode(GravityCallbackContext context) {
		if (context == null) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] encrypted = cipher.doFinal(JSON.toJSONBytes(context));
			return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(ByteBuffer.allocate(1 + iv.length + encrypted.length)
					.put(TOKEN_VERSION)
					.put(iv)
					.put(encrypted)
					.array());
		}
		catch (RuntimeException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalStateException("unable to encode GRAVITY callback context", exception);
		}
	}

	public GravityCallbackContext decode(String token) {
		if (!StringUtils.hasText(token) || token.length() > MAX_TOKEN_LENGTH) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
		try {
			byte[] value = Base64.getUrlDecoder().decode(token);
			if (value.length <= 1 + IV_LENGTH + TAG_LENGTH_BITS / Byte.SIZE || value[0] != TOKEN_VERSION) {
				throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
			}
			byte[] iv = new byte[IV_LENGTH];
			System.arraycopy(value, 1, iv, 0, IV_LENGTH);
			byte[] encrypted = new byte[value.length - 1 - IV_LENGTH];
			System.arraycopy(value, 1 + IV_LENGTH, encrypted, 0, encrypted.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			return JSON.parseObject(cipher.doFinal(encrypted), GravityCallbackContext.class);
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID", exception);
		}
	}

}
