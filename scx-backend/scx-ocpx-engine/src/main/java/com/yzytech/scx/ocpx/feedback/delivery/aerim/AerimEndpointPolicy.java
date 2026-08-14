package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** AERIM HTTPS 监测地址的白名单与 SSRF 校验。 */
@Component
@RequiredArgsConstructor
public class AerimEndpointPolicy {

	private static final int MAX_URL_LENGTH = 5000;

	private final AerimProperties properties;

	public String requireAllowedHttpsUrl(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
			throw new IllegalArgumentException("unsafe AERIM URL");
		}
		try {
			URI uri = URI.create(value.trim()).normalize();
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
					|| uri.getPort() == 0 || uri.getPort() > 65535) {
				throw new IllegalArgumentException("unsafe AERIM URL");
			}
			String host = normalizeHost(uri.getHost());
			Set<String> allowedHosts = properties.getAllowedHosts()
				.stream()
				.map(this::normalizeHost)
				.filter(StringUtils::hasText)
				.collect(Collectors.toSet());
			if (!StringUtils.hasText(host) || !allowedHosts.contains(host)) {
				throw new IllegalArgumentException("AERIM host is not allowed");
			}
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
						|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
					throw new IllegalArgumentException("unsafe AERIM URL");
				}
			}
			return uri.toASCIIString();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("unsafe AERIM URL", exception);
		}
	}

	private String normalizeHost(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String host = value.trim().toLowerCase(Locale.ROOT);
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		return host.contains(":") ? host : IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
	}

}
