package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
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

	private final SecretKeySpec blindIndexKey;

	private final SecureRandom random = new SecureRandom();

	public ProtocolCrypto(OcpxRuntimeProperties properties) {
		try {
			byte[] keyBytes = MessageDigest.getInstance("SHA-256")
				.digest(properties.getPayloadAesKey().getBytes(StandardCharsets.UTF_8));
			this.key = new SecretKeySpec(keyBytes, "AES");
			byte[] blindIndexBytes = MessageDigest.getInstance("SHA-256")
				.digest(("SCX-BLIND-INDEX|" + properties.getPayloadAesKey()).getBytes(StandardCharsets.UTF_8));
			this.blindIndexKey = new SecretKeySpec(blindIndexBytes, "HmacSHA256");
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot initialize protocol encryption", e);
		}
	}

	/**
	 * Stable keyed digest for equality/uniqueness checks on encrypted identifiers. The
	 * purpose is domain-separated and the original value is never recoverable from the
	 * index. It reuses the configured protocol key material and adds no new key path.
	 */
	public String blindIndex(String purpose, String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(blindIndexKey);
			byte[] digest = mac.doFinal((purpose + "\u0000" + value).getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot calculate protocol blind index", e);
		}
	}

	/**
	 * Signs a short-lived management token with the existing, domain-separated protocol
	 * key material. The key itself never leaves this component.
	 */
	public String signToken(String purpose, String payload) {
		String encoded = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		return encoded + "." + blindIndex("TOKEN|" + purpose, encoded);
	}

	public String verifyToken(String purpose, String token) {
		if (token == null || token.length() > 4096) {
			throw new IllegalArgumentException("Invalid signed token");
		}
		int separator = token.lastIndexOf('.');
		if (separator < 1 || separator == token.length() - 1) {
			throw new IllegalArgumentException("Invalid signed token");
		}
		String encoded = token.substring(0, separator);
		String expected = blindIndex("TOKEN|" + purpose, encoded);
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
				token.substring(separator + 1).getBytes(StandardCharsets.US_ASCII))) {
			throw new IllegalArgumentException("Invalid signed token");
		}
		try {
			return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException error) {
			throw new IllegalArgumentException("Invalid signed token", error);
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
