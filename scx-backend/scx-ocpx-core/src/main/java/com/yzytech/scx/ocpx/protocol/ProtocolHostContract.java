package com.yzytech.scx.ocpx.protocol;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/** 管理端允许域名的无网络、确定性规范化规则。 */
public final class ProtocolHostContract {

	private static final int MAX_HOST_LENGTH = 253;

	private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

	private static final Pattern IPV4_LIKE = Pattern.compile("[0-9.]+");

	private ProtocolHostContract() {
	}

	/**
	 * 规范化管理员录入的精确域名。只接受域名本身，不接受协议、端口、路径、通配符或 IP。
	 * @param value 管理端输入
	 * @return 小写 ASCII 域名
	 */
	public static String normalizeConfiguredHost(String value) {
		if (value == null || value.isBlank() || value.length() > MAX_HOST_LENGTH
				|| value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("允许域名不能为空且不能超过 253 个字符");
		}
		String host = value.trim().toLowerCase(Locale.ROOT);
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.contains("://") || host.contains("/") || host.contains("\\") || host.contains(":")
				|| host.contains("?") || host.contains("#") || host.contains("*")
				|| IPV4_LIKE.matcher(host).matches()) {
			throw new IllegalArgumentException("只允许填写精确域名，不含协议、端口、路径、通配符或 IP");
		}
		try {
			host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("允许域名格式不正确", exception);
		}
		String[] labels = host.split("\\.", -1);
		if (host.length() > MAX_HOST_LENGTH || labels.length < 2) {
			throw new IllegalArgumentException("允许域名必须是完整域名");
		}
		for (String label : labels) {
			if (!LABEL.matcher(label).matches()) {
				throw new IllegalArgumentException("允许域名格式不正确");
			}
		}
		return host;
	}

}
