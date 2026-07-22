package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.config.TrackingProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ChannelCredentialCrypto {

	private static final int GCM_TAG_BITS = 128;

	private final TrackingProperties properties;

	public EncryptedValue encrypt(String plaintext) {
		try {
			byte[] iv = new byte[12];
			new SecureRandom().nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return new EncryptedValue(Base64.getEncoder().encodeToString(encrypted),
					Base64.getEncoder().encodeToString(iv));
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("渠道敏感值加密失败", exception);
		}
	}

	public String decrypt(String ciphertext, String iv) {
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, masterKey(),
					new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(iv)));
			return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new IllegalStateException("渠道敏感值解密失败", exception);
		}
	}

	private SecretKeySpec masterKey() {
		if (!StringUtils.hasText(properties.getChannelMasterKey())) {
			throw new IllegalStateException("未配置渠道凭据主密钥");
		}
		byte[] key;
		try {
			key = Base64.getDecoder().decode(properties.getChannelMasterKey());
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("渠道凭据主密钥必须使用 Base64", exception);
		}
		if (key.length != 32) {
			throw new IllegalStateException("渠道凭据主密钥必须为 256 位");
		}
		return new SecretKeySpec(key, "AES");
	}

	public record EncryptedValue(String ciphertext, String iv) {
	}

}
