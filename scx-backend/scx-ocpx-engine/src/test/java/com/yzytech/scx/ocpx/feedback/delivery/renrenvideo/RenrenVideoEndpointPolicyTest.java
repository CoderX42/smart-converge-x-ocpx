package com.yzytech.scx.ocpx.feedback.delivery.renrenvideo;

import com.yzytech.scx.ocpx.configuration.index.OcpxProtocolAllowedHostIndex;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import com.yzytech.scx.ocpx.protocol.security.ProtocolDnsResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenrenVideoEndpointPolicyTest {

	@Test
	void permitsExactHttpAndHttpsPublicHosts() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		hosts.replace(Map.of(OcpxUpstreamProtocol.RENREN_VIDEO, Set.of("monitor.example.com")));
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		when(dns.resolveAll("monitor.example.com")).thenReturn(new InetAddress[] { InetAddress.getByName("8.8.8.8") });
		RenrenVideoEndpointPolicy policy = new RenrenVideoEndpointPolicy(hosts, dns);

		assertThat(policy.requireAllowedHttpUrl("http://monitor.example.com/ad-action/click?userId=1"))
			.isEqualTo("http://monitor.example.com/ad-action/click?userId=1");
		assertThat(policy.requireAllowedHttpUrl("https://monitor.example.com/ad-action/click?userId=1"))
			.isEqualTo("https://monitor.example.com/ad-action/click?userId=1");
	}

	@Test
	void rejectsEmptyAllowlistOtherHostPrivateDnsAndAmbiguousUrls() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		RenrenVideoEndpointPolicy policy = new RenrenVideoEndpointPolicy(hosts, dns);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpUrl("http://monitor.example.com/ad-action/click"))
			.withMessage("RENREN_VIDEO_HOST_NOT_ALLOWED");

		hosts.replace(Map.of(OcpxUpstreamProtocol.RENREN_VIDEO, Set.of("monitor.example.com")));
		when(dns.resolveAll("monitor.example.com"))
			.thenReturn(new InetAddress[] { InetAddress.getByName("127.0.0.1") });
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpUrl("http://monitor.example.com/ad-action/click"))
			.withMessage("RENREN_VIDEO_ADDRESS_NOT_PUBLIC");
		for (String unsafe : Set.of("ftp://monitor.example.com/ad-action/click",
				"http://user@monitor.example.com/ad-action/click", "http://monitor.example.com/ad-action/click#x",
				"http://monitor.example.com\\@evil/ad-action/click", "http://other.example/ad-action/click")) {
			assertThatIllegalArgumentException().isThrownBy(() -> policy.requireAllowedHttpUrl(unsafe));
		}
	}

}
