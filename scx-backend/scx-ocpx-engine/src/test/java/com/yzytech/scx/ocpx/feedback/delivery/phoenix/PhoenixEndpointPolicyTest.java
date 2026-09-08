package com.yzytech.scx.ocpx.feedback.delivery.phoenix;

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

class PhoenixEndpointPolicyTest {

	@Test
	void acceptsCustomPathsOnAllowedPublicHost() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		hosts.replace(Map.of(OcpxUpstreamProtocol.PHOENIX, Set.of("api.feather.ifeng.com")));
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		when(dns.resolveAll("api.feather.ifeng.com"))
			.thenReturn(new InetAddress[] { InetAddress.getByName("8.8.8.8") });
		PhoenixEndpointPolicy policy = new PhoenixEndpointPolicy(hosts, dns);

		for (String path : Set.of("/d/cli", "/ocpx/d/cli", "/custom/click")) {
			String url = "https://api.feather.ifeng.com" + path + "?cid=123";
			assertThat(policy.requireAllowedHttpsUrl(url)).isEqualTo(url);
		}
	}

	@Test
	void stillRejectsEmptyAllowlistPrivateDnsAndUnsafeUrls() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		PhoenixEndpointPolicy policy = new PhoenixEndpointPolicy(hosts, dns);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpsUrl("https://api.feather.ifeng.com/ocpx/d/cli"))
			.withMessage("PHOENIX_HOST_NOT_ALLOWED");

		hosts.replace(Map.of(OcpxUpstreamProtocol.PHOENIX, Set.of("api.feather.ifeng.com")));
		when(dns.resolveAll("api.feather.ifeng.com"))
			.thenReturn(new InetAddress[] { InetAddress.getByName("127.0.0.1") });
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpsUrl("https://api.feather.ifeng.com/ocpx/d/cli"))
			.withMessage("PHOENIX_ADDRESS_NOT_PUBLIC");
		for (String unsafe : Set.of("http://api.feather.ifeng.com/ocpx/d/cli",
				"https://user@api.feather.ifeng.com/ocpx/d/cli", "https://api.feather.ifeng.com/ocpx/d/cli#x",
				"https://api.feather.ifeng.com\\@evil/ocpx/d/cli", "https://other.example/ocpx/d/cli")) {
			assertThatIllegalArgumentException().isThrownBy(() -> policy.requireAllowedHttpsUrl(unsafe));
		}
	}

}
