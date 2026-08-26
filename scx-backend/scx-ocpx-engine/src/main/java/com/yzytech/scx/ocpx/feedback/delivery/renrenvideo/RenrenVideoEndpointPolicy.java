package com.yzytech.scx.ocpx.feedback.delivery.renrenvideo;

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

/** 人人视频 HTTP/HTTPS 点击地址的精确白名单与 SSRF 防护。 */
@Component
@RequiredArgsConstructor
public class RenrenVideoEndpointPolicy {

	private static final int MAX_URL_LENGTH = 5000;

	private final OcpxProtocolAllowedHostIndex allowedHostIndex;

	private final ProtocolDnsResolver dnsResolver;

	public String requireAllowedHttpUrl(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
			throw new IllegalArgumentException("RENREN_VIDEO_URL_UNSAFE");
		}
		try {
			URI uri = URI.create(value.trim()).normalize();
			String scheme = uri.getScheme();
			if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getUserInfo() != null
					|| uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
				throw new IllegalArgumentException("RENREN_VIDEO_URL_UNSAFE");
			}
			String host = ProtocolHostContract.normalizeConfiguredHost(uri.getHost());
			Set<String> allowedHosts = allowedHostIndex.get(OcpxUpstreamProtocol.RENREN_VIDEO);
			if (!StringUtils.hasText(host) || !allowedHosts.contains(host)) {
				throw new IllegalArgumentException("RENREN_VIDEO_HOST_NOT_ALLOWED");
			}
			InetAddress[] addresses = dnsResolver.resolveAll(host);
			if (addresses.length == 0) {
				throw new IllegalArgumentException("RENREN_VIDEO_DNS_UNRESOLVED");
			}
			for (InetAddress address : addresses) {
				if (!ProtocolAddressPolicy.isPublic(address)) {
					throw new IllegalArgumentException("RENREN_VIDEO_ADDRESS_NOT_PUBLIC");
				}
			}
			return uri.toASCIIString();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("RENREN_VIDEO_URL_UNSAFE", exception);
		}
	}

}
