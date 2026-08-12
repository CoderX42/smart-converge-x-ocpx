package com.yzytech.scx.ocpx.shared.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/** 获取当前 Engine 实例网络地址的工具。 */
public final class IpUtil {

	private IpUtil() {
	}

	/**
	 * 遍历网卡并返回最后一个非回环 IPv4 地址，用作定时任务锁值。
	 * @return 未找到或枚举失败时返回 {@code null}
	 */
	public static String getInet4Address() {
		String ip = null;
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress address = addresses.nextElement();
					if (address instanceof Inet4Address && !"127.0.0.1".equals(address.getHostAddress())) {
						// 不提前返回，保持旧实现选择遍历过程中最后一个有效地址的行为。
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
