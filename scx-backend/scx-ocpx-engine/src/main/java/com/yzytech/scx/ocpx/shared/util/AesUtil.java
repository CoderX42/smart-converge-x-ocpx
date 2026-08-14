package com.yzytech.scx.ocpx.shared.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 上游 Callback 上下文的 AES 加解密工具。
 *
 * <p>
 * 使用 {@code AES/ECB/PKCS5Padding} 和 URL-safe Base64 以兼容现有上下游协议。该格式只提供保密性，
 * 不自带完整性校验，不能擅自替换密码模式或编码格式而不升级外部契约。
 */
public final class AesUtil {

	private static final String CIPHER_MODE = "AES/ECB/PKCS5Padding";

	private static final int PASSWORD_SIZE = 16;

	private AesUtil() {
	}

	/**
	 * 加密明文并编码为 URL-safe Base64。
	 * @return 加密失败时返回 {@code null}
	 */
	public static String encryptBase64(String clearText, String password) {
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_MODE);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(passwordBytes(password), "AES"));
			return Base64.getUrlEncoder().encodeToString(cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			return null;
		}
	}

	/**
	 * 解码 URL-safe Base64 并解密为 UTF-8 明文。
	 * @return 解密失败时返回 {@code null}
	 */
	public static String decryptBase64(String cipherText, String password) {
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_MODE);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(passwordBytes(password), "AES"));
			return new String(cipher.doFinal(Base64.getUrlDecoder().decode(cipherText)), StandardCharsets.UTF_8);
		}
		catch (Exception exception) {
			return null;
		}
	}

	private static byte[] passwordBytes(String password) {
		// 短密钥右侧补零到 16 字节；较长输入保持原字节，由 Cipher 校验 AES 合法长度。
		byte[] data = null;
		if (password != null) {
			byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
			if (password.length() < PASSWORD_SIZE) {
				System.arraycopy(bytes, 0, data = new byte[PASSWORD_SIZE], 0, bytes.length);
			}
			else {
				data = bytes;
			}
		}
		return data;
	}

}
