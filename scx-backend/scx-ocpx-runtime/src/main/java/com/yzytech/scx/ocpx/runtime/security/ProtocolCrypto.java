package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.runtime.config.OcpxCryptoProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class ProtocolCrypto {

	private final SecretKeySpec blindIndexKey;

	public ProtocolCrypto(OcpxCryptoProperties properties) {
		try {
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

}
