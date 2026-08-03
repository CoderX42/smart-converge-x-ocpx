package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

@Component
public class OutboundUrlValidator {

	private final OcpxRuntimeProperties properties;

	public OutboundUrlValidator(OcpxRuntimeProperties properties) {
		this.properties = properties;
	}

	public void validate(URI uri) {
		String scheme = uri.getScheme();
		if (!("https".equalsIgnoreCase(scheme) || properties.isAllowHttp() && "http".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("outbound URL must use HTTPS");
		}
		if (uri.getHost() == null || uri.getUserInfo() != null) {
			throw new IllegalArgumentException("outbound URL host is invalid");
		}
		if (properties.isAllowPrivateAddresses()) {
			return;
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
						|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
					throw new IllegalArgumentException("outbound URL resolves to a private address");
				}
			}
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("outbound URL cannot be resolved", e);
		}
	}

}
