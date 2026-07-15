

package com.yzytech.scx.common.core.util;

import cn.hutool.crypto.symmetric.AES;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AesUtils {

	private static final String AES_MODE = "ECB";

	private static final String AES_PADDING = "PKCS7Padding";

	public static String decrypt(String encodeKey, String value) {
		AES aes = new AES(AES_MODE, AES_PADDING, deriveKey(encodeKey));
		return aes.decryptStr(value);
	}

	/** Keeps a short configured passphrase compatible with AES-256 on both clients. */
	private static byte[] deriveKey(String encodeKey) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(encodeKey.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

}
