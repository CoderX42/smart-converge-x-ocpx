package com.yzytech.scx.ocpx.feedback.delivery.phoenix;

import com.yzytech.scx.ocpx.configuration.index.OcpxProtocolAllowedHostIndex;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import com.yzytech.scx.ocpx.protocol.PhoenixTemplateContract;
import com.yzytech.scx.ocpx.protocol.ProtocolHostContract;
import com.yzytech.scx.ocpx.protocol.security.ProtocolAddressPolicy;
import com.yzytech.scx.ocpx.protocol.security.ProtocolDnsResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 凤凰新闻出站地址的精确 HTTPS 白名单、DNS 公网和 SSRF 校验。 */
@Component
@RequiredArgsConstructor
public class PhoenixEndpointPolicy {

	private static final int MAX_URL_LENGTH = 5000;

	private final OcpxProtocolAllowedHostIndex allowedHostIndex;

	private final ProtocolDnsResolver dnsResolver;

	public String requireAllowedHttpsUrl(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
			throw new IllegalArgumentException("PHOENIX_URL_UNSAFE");
		}
		try {
			if (!value.equals(value.trim())) {
				throw new IllegalArgumentException("PHOENIX_URL_UNSAFE");
			}
			URI uri = URI.create(value);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
					|| uri.getPort() != -1 || !StringUtils.hasText(uri.getHost()) || unsafeQuery(uri.getRawQuery())) {
				throw new IllegalArgumentException("PHOENIX_URL_UNSAFE");
			}
			String host = ProtocolHostContract.normalizeConfiguredHost(uri.getHost());
			Set<String> allowedHosts = allowedHostIndex.get(OcpxUpstreamProtocol.PHOENIX);
			if (!PhoenixTemplateContract.HOST.equals(host) || !allowedHosts.contains(host)) {
				throw new IllegalArgumentException("PHOENIX_HOST_NOT_ALLOWED");
			}
			InetAddress[] addresses = dnsResolver.resolveAll(host);
			if (addresses.length == 0) {
				throw new IllegalArgumentException("PHOENIX_DNS_UNRESOLVED");
			}
			for (InetAddress address : addresses) {
				if (!ProtocolAddressPolicy.isPublic(address)) {
					throw new IllegalArgumentException("PHOENIX_ADDRESS_NOT_PUBLIC");
				}
			}
			return uri.toASCIIString();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("PHOENIX_URL_UNSAFE", exception);
		}
	}

	private boolean unsafeQuery(String rawQuery) {
		if (rawQuery == null) {
			return false;
		}
		try {
			String decoded = URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
			return decoded.chars().anyMatch(Character::isISOControl);
		}
		catch (IllegalArgumentException exception) {
			return true;
		}
	}

}
