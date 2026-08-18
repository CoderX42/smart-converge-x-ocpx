package com.yzytech.scx.ocpx.feedback.delivery.aerim;

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

class AerimEndpointPolicyTest {

	@Test
	void allowsHttpAndHttpsOnlyForTheExactPublicAllowlistedHost() {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		hosts.replace(Map.of(OcpxUpstreamProtocol.AERIM, Set.of("track.example.com")));
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		try {
			when(dns.resolveAll("track.example.com"))
				.thenReturn(new InetAddress[] { InetAddress.getByName("8.8.8.8") });
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
		AerimEndpointPolicy policy = new AerimEndpointPolicy(hosts, dns);

		assertThat(policy.requireAllowedHttpUrl("https://track.example.com/ocpx/c?id=1"))
			.isEqualTo("https://track.example.com/ocpx/c?id=1");
		assertThat(policy.requireAllowedHttpUrl("http://track.example.com/ocpx/c?id=2"))
			.isEqualTo("http://track.example.com/ocpx/c?id=2");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpUrl("ftp://track.example.com/ocpx/c"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> policy.requireAllowedHttpUrl("http://other.example.com/ocpx/c"));
	}

	@Test
	void rejectsAnAllowlistedHostWhenDnsResolvesToPrivateSpace() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		hosts.replace(Map.of(OcpxUpstreamProtocol.AERIM, Set.of("track.example.com")));
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		when(dns.resolveAll("track.example.com")).thenReturn(new InetAddress[] { InetAddress.getByName("127.0.0.1") });

		assertThatIllegalArgumentException().isThrownBy(
				() -> new AerimEndpointPolicy(hosts, dns).requireAllowedHttpUrl("https://track.example.com/ocpx/c"));
	}

	@Test
	void rejectsAnAllowlistedHostWhenDnsReturnsNoAddress() throws Exception {
		OcpxProtocolAllowedHostIndex hosts = new OcpxProtocolAllowedHostIndex();
		hosts.replace(Map.of(OcpxUpstreamProtocol.AERIM, Set.of("track.example.com")));
		ProtocolDnsResolver dns = mock(ProtocolDnsResolver.class);
		when(dns.resolveAll("track.example.com")).thenReturn(new InetAddress[0]);

		assertThatIllegalArgumentException().isThrownBy(
				() -> new AerimEndpointPolicy(hosts, dns).requireAllowedHttpUrl("https://track.example.com/ocpx/c"));
	}

}
