package com.yzytech.scx.ocpx.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

public final class StringUtil {

	private StringUtil() {
	}

	public static String checkStrLength(String target, int length) {
		if (!StringUtils.hasText(target)) {
			return "";
		}
		else if (target.length() > length) {
			return target.substring(0, length);
		}
		return target;
	}

	public static int hashModulo(String key, Integer bucket) {
		int value = 0;
		if (StringUtils.hasText(key)) {
			value = (key.hashCode() & Integer.MAX_VALUE) % bucket;
		}
		return value;
	}

	public static String replaceDefault(String value, String macro) {
		if (StringUtils.hasText(value)) {
			return value;
		}
		return macro;
	}

	public static String replaceDefaultEncode(String value, String macro) {
		if (StringUtils.hasText(value)) {
			return NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8);
		}
		return macro;
	}

}
