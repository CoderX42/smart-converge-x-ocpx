package com.yzytech.scx.ocpx.protocol.security;

import java.net.InetAddress;

/** 上游协议目标地址的最小公网约束。 */
public final class ProtocolAddressPolicy {

	private ProtocolAddressPolicy() {
	}

	public static boolean isPublic(InetAddress address) {
		return !(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress());
	}

}
