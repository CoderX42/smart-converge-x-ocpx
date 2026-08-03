package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ProtocolCrypto {

	private static final int IV_LENGTH = 12;

	private final SecretKeySpec key;

	private final SecureRandom random = new SecureRandom();

	public ProtocolCrypto(OcpxRuntimeProperties properties) {
		try {
			byte[] keyBytes = MessageDigest.getInstance("SHA-256")
				.digest(properties.getPayloadAesKey().getBytes(StandardCharsets.UTF_8));
			this.key = new SecretKeySpec(keyBytes, "AES");
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot initialize protocol encryption", e);
		}
	}

	public String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot encrypt protocol snapshot", e);
		}
	}

	public String decrypt(String encoded) {
		try {
			byte[] data = Base64.getUrlDecoder().decode(encoded);
			ByteBuffer buffer = ByteBuffer.wrap(data);
			byte[] iv = new byte[IV_LENGTH];
			buffer.get(iv);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Invalid encrypted protocol snapshot", e);
		}
	}

}
