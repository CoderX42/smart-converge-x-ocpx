package com.yzytech.scx.ocpx.shared.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/** OCPX 协议字符串清洗、分桶与宏默认值工具。 */
public final class StringUtil {

	private StringUtil() {
	}

	/**
	 * 将空白值规范为空字符串，并截断超过数据库列上限的文本。
	 * @param target 原始文本
	 * @param length 最大 Java 字符数
	 * @return 规范化后的文本
	 */
	public static String checkStrLength(String target, int length) {
		if (!StringUtils.hasText(target)) {
			return "";
		}
		else if (target.length() > length) {
			return target.substring(0, length);
		}
		return target;
	}

	/**
	 * 使用非负 Java hash 将键映射到固定分桶。
	 * @return 空键映射到 0，否则返回 {@code [0, bucket)} 内的分桶号
	 */
	public static int hashModulo(String key, Integer bucket) {
		int value = 0;
		if (StringUtils.hasText(key)) {
			value = (key.hashCode() & Integer.MAX_VALUE) % bucket;
		}
		return value;
	}

	/** 字段非空时返回字段值，否则保留 URL 模板宏。 */
	public static String replaceDefault(String value, String macro) {
		if (StringUtils.hasText(value)) {
			return value;
		}
		return macro;
	}

	/** 字段非空时返回严格 URL 编码值，否则保留 URL 模板宏。 */
	public static String replaceDefaultEncode(String value, String macro) {
		if (StringUtils.hasText(value)) {
			return NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8);
		}
		return macro;
	}

}
