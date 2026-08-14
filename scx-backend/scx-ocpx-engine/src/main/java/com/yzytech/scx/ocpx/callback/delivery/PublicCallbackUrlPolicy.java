package com.yzytech.scx.ocpx.callback.delivery;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** 通用媒体回调的出站 SSRF 防护。 */
@Component
public class PublicCallbackUrlPolicy {

	private static final int MAX_URL_LENGTH = 5000;

	private static final Set<String> RESERVED_SUFFIXES = Set.of("localhost", "local", "internal", "home", "lan", "test",
			"example", "invalid", "onion");

	/**
	 * 校验并规范化通用媒体回调地址。
	 * @param value Feedback 携带并已解码的 URL
	 * @return 可用于出站请求的规范 URL
	 */
	public String requirePublicHttpUrl(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
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
			if (!StringUtils.hasText(host) || reservedHost(host)) {
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

	String appendEventType(String callbackUrl, int eventType) {
		String separator = callbackUrl.contains("?") ? "&" : "?";
		return callbackUrl + separator + "event_type=" + eventType;
	}

	String appendCallbackFields(String callbackUrl, int eventType, BigDecimal currencyAmount) {
		String value = appendEventType(callbackUrl, eventType);
		return currencyAmount == null ? value : value + "&currency_amount=" + currencyAmount.toPlainString();
	}

	private String normalizeHost(String value) {
		if (!StringUtils.hasText(value)) {
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

}
