package com.yzytech.scx.ocpx.feedback.delivery.gravity;

import com.yzytech.scx.ocpx.configuration.index.OcpxProtocolAllowedHostIndex;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import com.yzytech.scx.ocpx.protocol.ProtocolHostContract;
import com.yzytech.scx.ocpx.protocol.security.ProtocolAddressPolicy;
import com.yzytech.scx.ocpx.protocol.security.ProtocolDnsResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

/** 引力 HTTPS 监测地址的精确白名单与 SSRF 校验。 */
@Component
@RequiredArgsConstructor
public class GravityEndpointPolicy {

	private static final int MAX_URL_LENGTH = 5000;

	private final OcpxProtocolAllowedHostIndex allowedHostIndex;

	private final ProtocolDnsResolver dnsResolver;

	public String requireAllowedHttpsUrl(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| value.chars().anyMatch(Character::isISOControl) || value.contains("\\") || value.contains("{")
				|| value.contains("}")) {
			throw new IllegalArgumentException("GRAVITY_URL_UNSAFE");
		}
		try {
			URI uri = URI.create(value.trim()).normalize();
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
					|| uri.getPort() != -1) {
				throw new IllegalArgumentException("GRAVITY_URL_UNSAFE");
			}
			String host = ProtocolHostContract.normalizeConfiguredHost(uri.getHost());
			Set<String> allowedHosts = allowedHostIndex.get(OcpxUpstreamProtocol.GRAVITY);
			if (!StringUtils.hasText(host) || !allowedHosts.contains(host)) {
				throw new IllegalArgumentException("GRAVITY_HOST_NOT_ALLOWED");
			}
			InetAddress[] addresses = dnsResolver.resolveAll(host);
			if (addresses.length == 0) {
				throw new IllegalArgumentException("GRAVITY_DNS_UNSAFE");
			}
			for (InetAddress address : addresses) {
				if (!ProtocolAddressPolicy.isPublic(address)) {
					throw new IllegalArgumentException("GRAVITY_DNS_UNSAFE");
				}
			}
			return uri.toASCIIString();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("GRAVITY_URL_UNSAFE", exception);
		}
	}

}
