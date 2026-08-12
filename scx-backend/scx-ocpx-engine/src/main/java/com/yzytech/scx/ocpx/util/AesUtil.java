package com.yzytech.scx.ocpx.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class AesUtil {

	private static final String CIPHER_MODE = "AES/ECB/PKCS5Padding";

	private static final int PASSWORD_SIZE = 16;

	private AesUtil() {
	}

	public static String encryptBase64(String clearText, String password) {
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_MODE);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(passwordBytes(password), "AES"));
			return Base64.getUrlEncoder().encodeToString(cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			exception.printStackTrace();
			return null;
		}
	}

	public static String decryptBase64(String cipherText, String password) {
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_MODE);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(passwordBytes(password), "AES"));
			return new String(cipher.doFinal(Base64.getUrlDecoder().decode(cipherText)), StandardCharsets.UTF_8);
		}
		catch (Exception exception) {
			exception.printStackTrace();
			return null;
		}
	}

	private static byte[] passwordBytes(String password) {
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
