package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component
public class RelaySecurity {

	private final RelayProperties properties;

	private final StringRedisTemplate redis;

	public RelaySecurity(RelayProperties properties, StringRedisTemplate redis) {
		this.properties = properties;
		this.redis = redis;
	}

	public String secret(String ref) {
		String value = properties.getCredentials().get(ref);
		if (value == null || value.length() < 32)
			throw new RelayException(503, "RTA_CREDENTIAL_UNAVAILABLE");
		return value;
	}

	public static String hash(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static String sign(String secret, String method, String path, String key, String timestamp, String nonce,
			String body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String text = method + "\n" + path + "\n" + key + "\n" + timestamp + "\n" + nonce + "\n" + hash(body);
			return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public void verify(String ref, String path, String body, Map<String, String> headers, long now) {
		String key = headers.get("x-rta-key"), ts = headers.get("x-rta-timestamp"), nonce = headers.get("x-rta-nonce"),
				signature = headers.get("x-rta-sign");
		if (!ref.equals(key) || ts == null || !ts.matches("[0-9]{13}") || nonce == null
				|| !nonce.matches("[A-Za-z0-9_-]{16,128}") || signature == null || !signature.matches("[0-9a-f]{64}"))
			throw new RelayException(401, "RTA_SIGNATURE_INVALID");
		if (Math.abs(now - Long.parseLong(ts)) > 300_000)
			throw new RelayException(401, "RTA_SIGNATURE_EXPIRED");
		String expected = sign(secret(ref), "POST", path, key, ts, nonce, body);
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
				signature.getBytes(StandardCharsets.US_ASCII)))
			throw new RelayException(401, "RTA_SIGNATURE_INVALID");
		if (!Boolean.TRUE.equals(redis.opsForValue()
			.setIfAbsent(RtaContract.PREFIX + "nonce:" + hash(ref + ":" + nonce), "1", Duration.ofMinutes(10))))
			throw new RelayException(401, "RTA_REPLAY");
	}

	public Map<String, String> headers(String ref, String path, String body) {
		String timestamp = Long.toString(System.currentTimeMillis()), nonce = UUID.randomUUID().toString();
		return Map.of("X-Rta-Key", ref, "X-Rta-Timestamp", timestamp, "X-Rta-Nonce", nonce, "X-Rta-Sign",
				sign(secret(ref), "POST", path, ref, timestamp, nonce, body));
	}

	public String encrypt(String id, String plain) {
		return crypt(id, plain, true);
	}

	public String decrypt(String id, String encoded) {
		return crypt(id, encoded, false);
	}

	private String crypt(String id, String input, boolean encrypt) {
		try {
			byte[] key = Base64.getDecoder().decode(properties.getPayloadKey());
			if (key.length != 32)
				throw new IllegalArgumentException();
			byte[] data = encrypt ? input.getBytes(StandardCharsets.UTF_8) : Base64.getDecoder().decode(input);
			byte[] iv = new byte[12];
			if (encrypt)
				new SecureRandom().nextBytes(iv);
			else
				System.arraycopy(data, 0, iv, 0, 12);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
					new GCMParameterSpec(128, iv));
			cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
			if (!encrypt)
				return new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
			byte[] ciphertext = cipher.doFinal(data), out = new byte[12 + ciphertext.length];
			System.arraycopy(iv, 0, out, 0, 12);
			System.arraycopy(ciphertext, 0, out, 12, ciphertext.length);
			return Base64.getEncoder().encodeToString(out);
		}
		catch (Exception e) {
			throw new RelayException(503, "RTA_PAYLOAD_KEY_UNAVAILABLE");
		}
	}

}
