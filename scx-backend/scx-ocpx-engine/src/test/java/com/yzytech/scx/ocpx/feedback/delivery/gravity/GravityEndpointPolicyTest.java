package com.yzytech.scx.ocpx.feedback.delivery.gravity;

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

class GravityEndpointPolicyTest {

	@Test
	void permitsOnlyExactHttpsPublicHostWithoutUrlAmbiguities() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		hosts.replace(Map.of(OcpxUpstreamProtocol.GRAVITY, Set.of("backend.gravity-engine.com")));
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		when(dns.resolveAll("backend.gravity-engine.com"))
			.thenReturn(new InetAddress[] { InetAddress.getByName("8.8.8.8") });
		GravityEndpointPolicy policy = new GravityEndpointPolicy(hosts, dns);

		assertThat(policy.requireAllowedHttpsUrl("https://backend.gravity-engine.com/click/?idfa=x"))
			.isEqualTo("https://backend.gravity-engine.com/click/?idfa=x");
		for (String unsafe : Set.of("http://backend.gravity-engine.com/click", "https://other.example/click",
				"https://user@backend.gravity-engine.com/click", "https://backend.gravity-engine.com:444/click",
				"https://backend.gravity-engine.com/click#fragment", "https://backend.gravity-engine.com\\@evil/click",
				"https://backend.gravity-engine.com/click?idfa={idfa}")) {
			assertThatIllegalArgumentException().isThrownBy(() -> policy.requireAllowedHttpsUrl(unsafe));
		}
	}

	@Test
	void rejectsEmptyAllowlistPrivateDnsAndExcessiveUrl() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		GravityEndpointPolicy policy = new GravityEndpointPolicy(hosts, dns);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpsUrl("https://backend.gravity-engine.com/click"));

		hosts.replace(Map.of(OcpxUpstreamProtocol.GRAVITY, Set.of("backend.gravity-engine.com")));
		when(dns.resolveAll("backend.gravity-engine.com"))
			.thenReturn(new InetAddress[] { InetAddress.getByName("127.0.0.1") });
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpsUrl("https://backend.gravity-engine.com/click"));
		assertThatIllegalArgumentException().isThrownBy(
				() -> policy.requireAllowedHttpsUrl("https://backend.gravity-engine.com/click?q=" + "x".repeat(5000)));
	}

}
