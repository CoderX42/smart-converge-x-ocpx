package com.yzytech.scx.ocpx.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public final class IpUtil {

	private IpUtil() {
	}

	public static String getInet4Address() {
		String ip = null;
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress address = addresses.nextElement();
					if (address instanceof Inet4Address && !"127.0.0.1".equals(address.getHostAddress())) {
						ip = address.getHostAddress();
					}
				}
			}
		}
		catch (SocketException exception) {
			exception.printStackTrace();
		}
		return ip;
	}

}
