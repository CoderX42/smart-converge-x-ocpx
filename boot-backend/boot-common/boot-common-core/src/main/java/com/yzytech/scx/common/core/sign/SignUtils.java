/**
 * Copyright (c) 2025 江苏瑶知云科技有限公司
 * All rights reserved.
 */

package com.yzytech.scx.common.core.sign;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HmacSHA256(timestamp + nonce, secret)。
 */
public final class SignUtils {

	private SignUtils() {
	}

	/**
	 * X-Timestamp、X-Nonce、X-Sign 均未携带（无文本）。用于兼容未接入验签的旧版客户端；若只传了其中部分仍会走 {@link #checkSign}
	 * 并校验失败。
	 */
	public static boolean areApiSignHeadersAllBlank(String timestamp, String nonce, String sign) {
		return !StringUtils.hasText(timestamp) && !StringUtils.hasText(nonce) && !StringUtils.hasText(sign);
	}

	public static boolean checkSign(String timestamp, String nonce, String sign, String secret, long expireMs,
			long clockSkewMs) {
		if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(sign)
				|| !StringUtils.hasText(secret)) {
			return false;
		}

		final long ts;
		try {
			ts = Long.parseLong(timestamp.trim());
		}
		catch (NumberFormatException e) {
			return false;
		}

		long now = System.currentTimeMillis();
		if (ts - now > clockSkewMs) {
			return false;
		}
		if (now - ts > expireMs) {
			return false;
		}

		String src = timestamp + nonce;
		String localSign = hmacSha256(src, secret);
		return localSign.equalsIgnoreCase(sign.trim());
	}

	public static String hmacSha256(String data, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			mac.init(key);
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (Exception e) {
			return "";
		}
	}

	public static String sha256(String str) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(str.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e) {
			return "";
		}
	}

	/** Redis 中记录 nonce 的 key（对 nonce 做短哈希，避免 key 过长） */
	public static String nonceRedisKey(String nonce) {
		String hex = sha256(nonce);
		String suffix = hex.length() >= 32 ? hex.substring(0, 32) : hex;
		return ApiSignConstants.NONCE_KEY_PREFIX + suffix;
	}

}
