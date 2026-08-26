package com.yzytech.scx.ocpx.protocol.registry;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdvertiserProtocolRegistryTest {

	@Test
	void resolvesOnlyDatabaseConfiguredAdvertiserIds() {
		OcpxAdvertiserIndex index = new OcpxAdvertiserIndex();
		OcpxAdvertiser aerim = new OcpxAdvertiser();
		aerim.setId("aerim-advertiser");
		aerim.setUpstreamProtocol(OcpxUpstreamProtocol.AERIM);
		index.add(aerim.getId(), aerim);
		OcpxAdvertiser weifeng = new OcpxAdvertiser();
		weifeng.setId("weifeng-advertiser");
		weifeng.setUpstreamProtocol(OcpxUpstreamProtocol.WEIFENG);
		index.add(weifeng.getId(), weifeng);
		OcpxAdvertiser renrenVideo = new OcpxAdvertiser();
		renrenVideo.setId("renren-video-advertiser");
		renrenVideo.setUpstreamProtocol(OcpxUpstreamProtocol.RENREN_VIDEO);
		index.add(renrenVideo.getId(), renrenVideo);

		AdvertiserProtocolRegistry registry = new AdvertiserProtocolRegistry(index);

		assertThat(registry.resolve("aerim-advertiser")).contains(AdvertiserProtocolRegistry.Protocol.AERIM);
		assertThat(registry.resolve("weifeng-advertiser")).contains(AdvertiserProtocolRegistry.Protocol.WEIFENG);
		assertThat(registry.resolve("renren-video-advertiser"))
			.contains(AdvertiserProtocolRegistry.Protocol.RENREN_VIDEO);
		assertThat(registry.resolve("missing")).isEmpty();
	}

}
