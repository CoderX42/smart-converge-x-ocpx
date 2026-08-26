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
		if (address instanceof Inet4Address) {
			int first = unsigned(bytes[0]);
			int second = unsigned(bytes[1]);
			int third = unsigned(bytes[2]);
			return first != 0 && first != 10 && first != 127 && !(first == 100 && second >= 64 && second <= 127)
					&& !(first == 169 && second == 254) && !(first == 172 && second >= 16 && second <= 31)
					&& !(first == 192 && second == 0 && (third == 0 || third == 2))
					&& !(first == 192 && second == 88 && third == 99) && !(first == 192 && second == 168)
					&& !(first == 198 && (second == 18 || second == 19))
					&& !(first == 198 && second == 51 && third == 100) && !(first == 203 && second == 0 && third == 113)
					&& first < 224;
		}
		if (address instanceof Inet6Address) {
			return (unsigned(bytes[0]) & 0xfe) != 0xfc && !(unsigned(bytes[0]) == 0x20 && unsigned(bytes[1]) == 0x01
					&& unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8);
		}
		return false;
	}

	private static int unsigned(byte value) {
		return value & 0xff;
	}

}
