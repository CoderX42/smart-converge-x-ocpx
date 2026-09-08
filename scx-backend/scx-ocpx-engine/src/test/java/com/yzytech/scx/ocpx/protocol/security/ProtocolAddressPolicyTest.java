package com.yzytech.scx.ocpx.protocol.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolAddressPolicyTest {

	@Test
	void acceptsPublicAddressesAndRejectsPrivateReservedAndDocumentationRanges() throws Exception {
		assertThat(ProtocolAddressPolicy.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
		assertThat(ProtocolAddressPolicy.isPublic(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();

		for (String value : List.of("127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.1", "172.16.0.1", "192.168.0.1",
				"192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "fc00::1", "2001:db8::1")) {
			assertThat(ProtocolAddressPolicy.isPublic(InetAddress.getByName(value))).as(value).isFalse();
		}
	}

	@Test
	void rejectsIpv4MappedPrivateAddressesEvenWhenRepresentedAsIpv6() throws Exception {
		byte[] privateMapped = new byte[16];
		privateMapped[10] = (byte) 0xff;
		privateMapped[11] = (byte) 0xff;
		privateMapped[12] = 10;
		privateMapped[15] = 1;
		Inet6Address address = Inet6Address.getByAddress(null, privateMapped, -1);

		assertThat(ProtocolAddressPolicy.isPublic(address)).isFalse();
	}

	@Test
	void rejectsDeprecatedIpv4CompatibleIpv6Addresses() throws Exception {
		byte[] compatible = new byte[16];
		compatible[12] = 8;
		compatible[13] = 8;
		compatible[14] = 8;
		compatible[15] = 8;
		Inet6Address address = Inet6Address.getByAddress(null, compatible, -1);

		assertThat(ProtocolAddressPolicy.isPublic(address)).isFalse();
	}

	@Test
	void rejectsScopedIpv6Addresses() throws Exception {
		Inet6Address address = Inet6Address.getByAddress(null,
				InetAddress.getByName("2606:4700:4700::1111").getAddress(), 2);

		assertThat(ProtocolAddressPolicy.isPublic(address)).isFalse();
	}

}
