package com.yzytech.scx.ocpx.audit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class TestLegacyAes {

	private TestLegacyAes() {
	}

	static String encrypt(String value, String password) {
		try {
			byte[] key = password.getBytes(StandardCharsets.UTF_8);
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
			return Base64.getUrlEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

}
