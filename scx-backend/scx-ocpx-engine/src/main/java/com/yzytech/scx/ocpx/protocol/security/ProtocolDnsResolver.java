package com.yzytech.scx.ocpx.protocol.security;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** 独立 DNS 解析边界，便于安全策略在测试中覆盖重绑定和私网地址。 */
@Component
public class ProtocolDnsResolver {

	public InetAddress[] resolveAll(String host) throws UnknownHostException {
		return InetAddress.getAllByName(host);
	}

}
