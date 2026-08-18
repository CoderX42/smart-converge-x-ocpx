package com.yzytech.scx.ocpx.feedback.delivery.aerim;

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

/** AERIM HTTP/HTTPS 监测地址的白名单与 SSRF 校验。 */
@Component
@RequiredArgsConstructor
public class AerimEndpointPolicy {

	private static final int MAX_URL_LENGTH = 5000;

	private final OcpxProtocolAllowedHostIndex allowedHostIndex;

	private final ProtocolDnsResolver dnsResolver;

	public String requireAllowedHttpUrl(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
			throw new IllegalArgumentException("unsafe AERIM URL");
		}
		try {
			URI uri = URI.create(value.trim()).normalize();
			String scheme = uri.getScheme();
			if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getUserInfo() != null
					|| uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
				throw new IllegalArgumentException("unsafe AERIM URL");
			}
			String host = ProtocolHostContract.normalizeConfiguredHost(uri.getHost());
			Set<String> allowedHosts = allowedHostIndex.get(OcpxUpstreamProtocol.AERIM);
			if (!StringUtils.hasText(host) || !allowedHosts.contains(host)) {
				throw new IllegalArgumentException("AERIM host is not allowed");
			}
			InetAddress[] addresses = dnsResolver.resolveAll(host);
			if (addresses.length == 0) {
				throw new IllegalArgumentException("unsafe AERIM URL");
			}
			for (InetAddress address : addresses) {
				if (!ProtocolAddressPolicy.isPublic(address)) {
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

}
