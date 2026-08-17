package com.yzytech.scx.ocpx.protocol.registry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdvertiserProtocolRegistryTest {

	@Test
	void resolvesOnlyServerConfiguredAdvertiserIds() {
		OcpxProtocolProperties properties = new OcpxProtocolProperties();
		properties.setAdvertisers(Map.of("aerim-advertiser", AdvertiserProtocolRegistry.Protocol.AERIM,
				"weifeng-advertiser", AdvertiserProtocolRegistry.Protocol.WEIFENG));

		AdvertiserProtocolRegistry registry = new AdvertiserProtocolRegistry(properties);

		assertThat(registry.resolve("aerim-advertiser")).contains(AdvertiserProtocolRegistry.Protocol.AERIM);
		assertThat(registry.resolve("weifeng-advertiser")).contains(AdvertiserProtocolRegistry.Protocol.WEIFENG);
		assertThat(registry.resolve("missing")).isEmpty();
	}

}
