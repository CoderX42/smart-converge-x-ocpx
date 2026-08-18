package com.yzytech.scx.ocpx.contract;

import java.math.BigDecimal;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 媒体 Callback URL 的共享安全契约。
 *
 * <p>Engine 正式回传和管理端联调回调必须使用同一套公网地址校验与参数拼接规则，避免两条链路产生协议漂移。</p>
 */
public class PublicCallbackUrlContract {

	private static final int MAX_URL_LENGTH = 5000;

	private static final Set<String> RESERVED_SUFFIXES = Set.of("localhost", "local", "internal", "home", "lan",
			"test", "example", "invalid", "onion");

	/** 校验并规范化可公开访问的 HTTP(S) Callback URL。 */
	public String requirePublicHttpUrl(String value) {
		if (!hasText(value) || value.length() > MAX_URL_LENGTH || value.chars().anyMatch(Character::isISOControl)
				|| value.contains("\\")) {
			throw new IllegalArgumentException("unsafe callback URL");
		}
		try {
			URI uri = URI.create(value.trim()).normalize();
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getUserInfo() != null
					|| uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
				throw new IllegalArgumentException("unsafe callback URL");
			}
			String host = normalizeHost(uri.getHost());
			if (!hasText(host) || reservedHost(host)) {
				throw new IllegalArgumentException("unsafe callback URL");
			}
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
						|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
					throw new IllegalArgumentException("unsafe callback URL");
				}
			}
			return uri.toASCIIString();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("unsafe callback URL", exception);
		}
	}

	/** 按正式媒体回传协议追加事件编号和可选金额。 */
	public String appendCallbackFields(String callbackUrl, int eventType, BigDecimal currencyAmount) {
		String separator = callbackUrl.contains("?") ? "&" : "?";
		String value = callbackUrl + separator + "event_type=" + eventType;
		return currencyAmount == null ? value : value + "&currency_amount=" + currencyAmount.toPlainString();
	}

	private String normalizeHost(String value) {
		if (!hasText(value)) {
			return null;
		}
		String host = value.toLowerCase(Locale.ROOT);
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.contains(":")) {
			return host;
		}
		return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
	}

	private boolean reservedHost(String host) {
		String last = host.substring(host.lastIndexOf('.') + 1);
		return RESERVED_SUFFIXES.contains(host) || RESERVED_SUFFIXES.contains(last);
	}

	private boolean hasText(String value) {
		return value != null && !value.trim().isEmpty();
	}

}
