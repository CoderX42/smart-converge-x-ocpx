package com.yzytech.scx.ocpx.audit;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OCPX Callback 与原始审计共用根密钥、按用途派生子密钥的多版本密钥环。 */
public final class OcpxKeyRingCrypto {

	public static final String PURPOSE_AUDIT = "SCX-OCPX-WIRE-AUDIT-v1";

	public static final String PURPOSE_CALLBACK = "SCX-OCPX-CALLBACK-v1";

	public static final String PURPOSE_TEST_SIGNING = "SCX-OCPX-TEST-SIGNING-v1";

	private static final int GCM_IV_BYTES = 12;

	private static final int GCM_TAG_BITS = 128;

	private final SecureRandom random = new SecureRandom();

	private final String activeKeyId;

	private final Map<String, String> keys;

	private final List<String> legacyOrder;

	public OcpxKeyRingCrypto(String activeKeyId, Map<String, String> keys, List<String> legacyOrder) {
		if (activeKeyId == null || activeKeyId.isBlank() || keys == null || !keys.containsKey(activeKeyId)) {
			throw new IllegalArgumentException("active callback key id is missing from key ring");
		}
		Map<String, String> validated = new LinkedHashMap<>();
		keys.forEach((id, key) -> {
			if (id == null || id.isBlank() || key == null || key.isBlank()) {
				throw new IllegalArgumentException("callback key ring contains a blank id or key");
			}
			validated.put(id, key);
		});
		this.activeKeyId = activeKeyId;
		this.keys = Map.copyOf(validated);
		List<String> order = new ArrayList<>();
		if (legacyOrder != null) {
			legacyOrder.stream().filter(validated::containsKey).forEach(order::add);
		}
		validated.keySet().stream().filter(id -> !order.contains(id)).forEach(order::add);
		this.legacyOrder = List.copyOf(order);
	}

	public String activeKeyId() {
		return activeKeyId;
	}

	public SealedValue encrypt(byte[] clearText, String purpose, byte[] aad) {
		return encrypt(activeKeyId, clearText, purpose, aad);
	}

	public SealedValue encrypt(String keyId, byte[] clearText, String purpose, byte[] aad) {
		try {
			byte[] nonce = new byte[GCM_IV_BYTES];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derivedKey(keyId, purpose), "AES"),
					new GCMParameterSpec(GCM_TAG_BITS, nonce));
			if (aad != null) {
				cipher.updateAAD(aad);
			}
			return new SealedValue(keyId, 1, nonce, cipher.doFinal(clearText));
		}
		catch (Exception exception) {
			throw new IllegalStateException("unable to encrypt OCPX protected payload", exception);
		}
	}

	public byte[] decrypt(SealedValue value, String purpose, byte[] aad) {
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derivedKey(value.keyId(), purpose), "AES"),
					new GCMParameterSpec(GCM_TAG_BITS, value.nonce()));
			if (aad != null) {
				cipher.updateAAD(aad);
			}
			return cipher.doFinal(value.cipherText());
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("unable to decrypt OCPX protected payload", exception);
		}
	}

	public String encryptCallback(String clearText) {
		SealedValue value = encrypt(clearText.getBytes(StandardCharsets.UTF_8), PURPOSE_CALLBACK,
				callbackAad(activeKeyId));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer
			.allocate(value.nonce().length + value.cipherText().length)
			.put(value.nonce())
			.put(value.cipherText())
			.array());
	}

	/** 带 kid 使用认证新格式；无 kid 时只尝试现网 AES/ECB 历史格式。 */
	public String decryptCallback(String keyId, String token) {
		if (keyId == null || keyId.isBlank()) {
			for (String candidate : legacyOrder) {
				String result = decryptLegacy(token, keys.get(candidate));
				if (result != null) {
					return result;
				}
			}
			throw new IllegalArgumentException("invalid legacy callback context");
		}
		if (!keys.containsKey(keyId)) {
			throw new IllegalArgumentException("unknown callback key id");
		}
		try {
			byte[] packed = Base64.getUrlDecoder().decode(token);
			if (packed.length <= GCM_IV_BYTES + GCM_TAG_BITS / Byte.SIZE) {
				throw new IllegalArgumentException("invalid callback context");
			}
			byte[] nonce = new byte[GCM_IV_BYTES];
			byte[] cipherText = new byte[packed.length - GCM_IV_BYTES];
			System.arraycopy(packed, 0, nonce, 0, nonce.length);
			System.arraycopy(packed, nonce.length, cipherText, 0, cipherText.length);
			return new String(decrypt(new SealedValue(keyId, 1, nonce, cipherText), PURPOSE_CALLBACK,
					callbackAad(keyId)), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("invalid callback context", exception);
		}
	}

	public byte[] signingKey(String keyId) {
		return derivedKey(keyId == null || keyId.isBlank() ? activeKeyId : keyId, PURPOSE_TEST_SIGNING);
	}

	/** 读取 V120 无 kid、SHA-256(root)+AES-GCM 的引力历史上下文。 */
	public byte[] decryptLegacyGravity(String token) {
		for (String keyId : legacyOrder) {
			try {
				byte[] value = Base64.getUrlDecoder().decode(token);
				if (value.length <= 1 + GCM_IV_BYTES + GCM_TAG_BITS / Byte.SIZE || value[0] != 1) {
					continue;
				}
				byte[] nonce = new byte[GCM_IV_BYTES];
				byte[] cipherText = new byte[value.length - 1 - GCM_IV_BYTES];
				System.arraycopy(value, 1, nonce, 0, nonce.length);
				System.arraycopy(value, 1 + nonce.length, cipherText, 0, cipherText.length);
				byte[] legacyKey = MessageDigest.getInstance("SHA-256")
					.digest(keys.get(keyId).getBytes(StandardCharsets.UTF_8));
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(legacyKey, "AES"),
						new GCMParameterSpec(GCM_TAG_BITS, nonce));
				return cipher.doFinal(cipherText);
			}
			catch (Exception ignored) {
			}
		}
		throw new IllegalArgumentException("invalid legacy gravity callback context");
	}

	private byte[] callbackAad(String keyId) {
		return ("callback|" + keyId).getBytes(StandardCharsets.UTF_8);
	}

	private byte[] derivedKey(String keyId, String purpose) {
		String root = keys.get(keyId);
		if (root == null) {
			throw new IllegalArgumentException("unknown OCPX key id");
		}
		try {
			byte[] salt = MessageDigest.getInstance("SHA-256")
				.digest(("SCX-OCPX-HKDF|" + purpose).getBytes(StandardCharsets.UTF_8));
			Mac extract = Mac.getInstance("HmacSHA256");
			extract.init(new SecretKeySpec(salt, "HmacSHA256"));
			byte[] pseudoRandomKey = extract.doFinal(root.getBytes(StandardCharsets.UTF_8));
			Mac expand = Mac.getInstance("HmacSHA256");
			expand.init(new SecretKeySpec(pseudoRandomKey, "HmacSHA256"));
			return expand.doFinal((purpose + "\u0001").getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("unable to derive OCPX domain key", exception);
		}
	}

	private String decryptLegacy(String cipherText, String password) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(legacyPasswordBytes(password), "AES"));
			return new String(cipher.doFinal(Base64.getUrlDecoder().decode(cipherText)), StandardCharsets.UTF_8);
		}
		catch (Exception exception) {
			return null;
		}
	}

	private byte[] legacyPasswordBytes(String password) {
		byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
		if (password.length() >= 16) {
			return bytes;
		}
		byte[] padded = new byte[16];
		System.arraycopy(bytes, 0, padded, 0, bytes.length);
		return padded;
	}

	public record SealedValue(String keyId, int version, byte[] nonce, byte[] cipherText) {
	}

}
