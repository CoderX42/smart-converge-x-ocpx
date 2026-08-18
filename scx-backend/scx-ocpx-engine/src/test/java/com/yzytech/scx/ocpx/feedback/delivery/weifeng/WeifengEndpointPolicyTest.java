package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import com.yzytech.scx.ocpx.configuration.index.OcpxProtocolAllowedHostIndex;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import com.yzytech.scx.ocpx.protocol.security.ProtocolDnsResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeifengEndpointPolicyTest {

	@Test
	void rejectsUnknownHostsWrongPathsAndLocalAddresses() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		hosts.replace(Map.of(OcpxUpstreamProtocol.WEIFENG, Set.of("ad.adapi.cn")));
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		when(dns.resolveAll("ad.adapi.cn")).thenReturn(new InetAddress[] { InetAddress.getByName("8.8.8.8") });
		WeifengEndpointPolicy policy = new WeifengEndpointPolicy(hosts, dns);

		assertThatThrownBy(() -> policy.requireAllowedClickUrl("https://example.com/click?aid=1"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy.requireAllowedClickUrl("http://ad.adapi.cn/not-click?aid=1"))
			.isInstanceOf(IllegalArgumentException.class);

		when(dns.resolveAll("ad.adapi.cn")).thenReturn(new InetAddress[] { InetAddress.getByName("127.0.0.1") });
		assertThatThrownBy(() -> policy.requireAllowedClickUrl("http://ad.adapi.cn/click?aid=1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
