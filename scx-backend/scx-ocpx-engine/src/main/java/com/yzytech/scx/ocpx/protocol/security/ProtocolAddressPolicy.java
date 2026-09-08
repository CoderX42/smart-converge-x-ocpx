package com.yzytech.scx.ocpx.protocol.security;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;

/** 上游协议目标地址的最小公网约束。 */
public final class ProtocolAddressPolicy {

	private ProtocolAddressPolicy() {
	}

	public static boolean isPublic(InetAddress address) {
		if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
				|| address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		// An IPv4-mapped IPv6 literal is represented as Inet6Address by the JDK. Treat
		// it as IPv4 before applying the private/reserved range checks; otherwise
		// ::ffff:10.0.0.1 (or a loopback/private equivalent) could pass the IPv6 branch.
		if (bytes.length == 16 && isIpv4Mapped(bytes)) {
			return isPublicIpv4(bytes[12] & 0xff, bytes[13] & 0xff, bytes[14] & 0xff, bytes[15] & 0xff);
		}
		// Deprecated IPv4-compatible (::/96) and IPv4-translated forms are not
		// globally routable IPv6 provider endpoints. Rejecting the whole prefix also
		// covers a private IPv4 suffix represented by Inet6Address.
		if (bytes.length == 16 && isIpv4Compatible(bytes)) {
			return false;
		}
		if (address instanceof Inet4Address) {
			return isPublicIpv4(unsigned(bytes[0]), unsigned(bytes[1]), unsigned(bytes[2]), unsigned(bytes[3]));
		}
		if (address instanceof Inet6Address) {
			// A scoped IPv6 literal is interface-local by definition; DNS provider
			// answers must never carry a scope that could steer traffic to a host-only
			// interface.
			if (((Inet6Address) address).getScopeId() != 0) {
				return false;
			}
			int first = unsigned(bytes[0]);
			int second = unsigned(bytes[1]);
			return (first & 0xfe) != 0xfc
					&& !(first == 0x20 && second == 0x01 && unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8)
					&& !(first == 0xfe && (second & 0xc0) == 0x80);
		}
		return false;
	}

	private static boolean isPublicIpv4(int first, int second, int third, int fourth) {
		return first != 0 && first != 10 && first != 127 && !(first == 100 && second >= 64 && second <= 127)
				&& !(first == 169 && second == 254) && !(first == 172 && second >= 16 && second <= 31)
				&& !(first == 192 && second == 0 && (third == 0 || third == 2))
				&& !(first == 192 && second == 88 && third == 99) && !(first == 192 && second == 168)
				&& !(first == 198 && (second == 18 || second == 19)) && !(first == 198 && second == 51 && third == 100)
				&& !(first == 203 && second == 0 && third == 113) && first < 224;
	}

	private static boolean isIpv4Mapped(byte[] bytes) {
		for (int index = 0; index < 10; index++) {
			if (bytes[index] != 0) {
				return false;
			}
		}
		return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
	}

	private static boolean isIpv4Compatible(byte[] bytes) {
		for (int index = 0; index < 12; index++) {
			if (bytes[index] != 0) {
				return false;
			}
		}
		return true;
	}

	private static int unsigned(byte value) {
		return value & 0xff;
	}

}
