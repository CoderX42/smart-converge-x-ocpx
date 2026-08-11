package com.yzytech.scx.ocpx.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcpxIntegrationServiceTest {

	@Test
	void returnsCopyableContractWithoutExposingChannelSecret() {
		OcpxMonitorLinkMapper monitorMapper = mock(OcpxMonitorLinkMapper.class);
		OcpxChannelMapper channelMapper = mock(OcpxChannelMapper.class);
		OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);
		OcpxAccessScope accessScope = mock(OcpxAccessScope.class);
		OcpxMonitorLink monitor = new OcpxMonitorLink();
		monitor.setId("monitor-1");
		monitor.setMonitorName("测试监测");
		monitor.setChannelId("channel-1");
		monitor.setAdvertiserId("advertiser-1");
		monitor.setClickUrl("https://scx.example/open-api/ocpx/v1/channels/demo/feedback");
		monitor.setRouteStatus("PUBLISHED");
		OcpxChannel channel = new OcpxChannel();
		channel.setId("channel-1");
		channel.setChannelCode("demo");
		channel.setAdapterCode("STANDARD");
		channel.setAuthType("HMAC");
		channel.setKeyVersion(2);
		channel.setSecretKey("never-return-this-secret-value");
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdapterCode("COCO");
		when(monitorMapper.selectById("monitor-1")).thenReturn(monitor);
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		OcpxIntegrationService service = new OcpxIntegrationService(monitorMapper, channelMapper,
				advertiserMapper, accessScope, new ObjectMapper());

		var integration = service.get("monitor-1");

		assertThat(integration.feedbackUrl()).isEqualTo(monitor.getClickUrl());
		assertThat(integration.requiredHeaders()).anyMatch(header -> header.startsWith("X-Sign:"));
		assertThat(integration.callbackPathPattern()).contains("/upstreams/coco/callback?ext=<SCX动态令牌>");
		assertThat(integration.toString()).doesNotContain(channel.getSecretKey());
	}

}
