package com.yzytech.scx.adx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import org.junit.jupiter.api.Test;

class AdxRoutePublishServiceTest {

	private final AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);

	private final AdxChannelMapper channelMapper = mock(AdxChannelMapper.class);

	private final AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);

	private final AdxConfigChangeService configChangeService = mock(AdxConfigChangeService.class);

	private final AdxAdapterService adapterService = mock(AdxAdapterService.class);

	private final AdxRoutePublishService service = new AdxRoutePublishService(monitorMapper, channelMapper,
			advertiserMapper, configChangeService, adapterService);

	@Test
	void rejectsRoutesWithoutHmac() {
		Fixture fixture = fixture();
		fixture.channel().setAuthType("NONE");
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HMAC");
	}

	@Test
	void rejectsHostOutsideExplicitAllowlist() {
		Fixture fixture = fixture();
		fixture.advertiser().setUpstreamHostAllowlist("other.example");
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("白名单");
	}

	@Test
	void rejectsPrivateDnsAddress() {
		Fixture fixture = fixture();
		fixture.advertiser().setBidUrl("https://127.0.0.1/click?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("127.0.0.1");
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("私网");
	}

	@Test
	void publishesVersionedRouteWithBothDirectionsConfigured() {
		Fixture fixture = fixture();
		fixture.monitor().setConfigVersion(4L);
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		fixture.advertiser().setBidUrl("https://8.8.8.8/click?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		stub(fixture);

		var result = service.publish("monitor-1");

		assertThat(result).containsEntry("routeStatus", "PUBLISHED").containsEntry("configVersion", 5L);
		verify(monitorMapper).updateById(fixture.monitor());
		verify(configChangeService).enqueueMonitor("monitor-1", 5L, "ROUTE_PUBLISHED");
	}

	private void stub(Fixture fixture) {
		doNothing().when(adapterService).requireEnabled(fixture.channel().getAdapterCode(), "DOWNSTREAM");
		doNothing().when(adapterService).requireEnabled(fixture.advertiser().getAdapterCode(), "UPSTREAM");
		when(monitorMapper.selectById("monitor-1")).thenReturn(fixture.monitor());
		when(channelMapper.selectById("channel-1")).thenReturn(fixture.channel());
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(fixture.advertiser());
	}

	private Fixture fixture() {
		AdxMonitorLink monitor = new AdxMonitorLink();
		monitor.setId("monitor-1");
		monitor.setChannelId("channel-1");
		monitor.setAdvertiserId("advertiser-1");
		monitor.setRtaMode("OFF");
		monitor.setBillingMode("OFF");
		AdxChannel channel = new AdxChannel();
		channel.setId("channel-1");
		channel.setAdapterCode("STANDARD");
		channel.setStatus("ACTIVE");
		channel.setAuthType("HMAC");
		channel.setSecretKey("12345678901234567890123456789012");
		channel.setCallbackHostAllowlist("callback.example");
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdapterCode("COCO");
		advertiser.setStatus("ACTIVE");
		advertiser.setBidUrl("https://coco.example/click?callback=__CALLBACK__");
		advertiser.setUpstreamHostAllowlist("coco.example");
		return new Fixture(monitor, channel, advertiser);
	}

	private record Fixture(AdxMonitorLink monitor, AdxChannel channel, AdxAdvertiser advertiser) {
	}

}
