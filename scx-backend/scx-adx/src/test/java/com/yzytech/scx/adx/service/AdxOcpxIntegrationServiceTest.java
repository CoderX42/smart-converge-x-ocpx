package com.yzytech.scx.adx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdxOcpxIntegrationServiceTest {

	@Test
	void returnsCopyableContractWithoutExposingChannelSecret() {
		AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);
		AdxChannelMapper channelMapper = mock(AdxChannelMapper.class);
		AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);
		AdxAccessScope accessScope = mock(AdxAccessScope.class);
		AdxMonitorLink monitor = new AdxMonitorLink();
		monitor.setId("monitor-1");
		monitor.setMonitorName("测试监测");
		monitor.setChannelId("channel-1");
		monitor.setAdvertiserId("advertiser-1");
		monitor.setClickUrl("https://scx.example/open-api/ocpx/v1/channels/demo/feedback");
		monitor.setRouteStatus("PUBLISHED");
		AdxChannel channel = new AdxChannel();
		channel.setId("channel-1");
		channel.setChannelCode("demo");
		channel.setAdapterCode("STANDARD");
		channel.setAuthType("HMAC");
		channel.setKeyVersion(2);
		channel.setSecretKey("never-return-this-secret-value");
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdapterCode("COCO");
		when(monitorMapper.selectById("monitor-1")).thenReturn(monitor);
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		AdxOcpxIntegrationService service = new AdxOcpxIntegrationService(monitorMapper, channelMapper,
				advertiserMapper, accessScope, new ObjectMapper());

		var integration = service.get("monitor-1");

		assertThat(integration.feedbackUrl()).isEqualTo(monitor.getClickUrl());
		assertThat(integration.requiredHeaders()).anyMatch(header -> header.startsWith("X-Sign:"));
		assertThat(integration.callbackPathPattern()).contains("/upstreams/coco/callback?ext=<SCX动态令牌>");
		assertThat(integration.toString()).doesNotContain(channel.getSecretKey());
	}

}
