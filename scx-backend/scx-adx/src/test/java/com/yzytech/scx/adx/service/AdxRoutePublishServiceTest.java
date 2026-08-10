package com.yzytech.scx.adx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

class AdxRoutePublishServiceTest {

	private final AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);

	private final AdxChannelMapper channelMapper = mock(AdxChannelMapper.class);

	private final AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);

	private final AdxConfigChangeService configChangeService = mock(AdxConfigChangeService.class);

	private final AdxAdapterService adapterService = mock(AdxAdapterService.class);

	private final AdxEventService eventService = mock(AdxEventService.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxRoutePublishService service = new AdxRoutePublishService(monitorMapper, channelMapper,
			advertiserMapper, configChangeService, adapterService, eventService, accessScope);

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
		assertThat(fixture.monitor().getClickDedupWindowSeconds()).isEqualTo(3600);
		verify(monitorMapper).updateById(fixture.monitor());
		verify(configChangeService).enqueueMonitor("monitor-1", 5L, "ROUTE_PUBLISHED");
	}

	@Test
	void monitorOverrideWinsAndIsFrozenWhenPublished() {
		Fixture fixture = fixture();
		fixture.advertiser().setBidUrl("https://127.0.0.1/default?callback=__CALLBACK__");
		fixture.monitor().setUpstreamFeedbackUrl("https://8.8.4.4/override?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.4.4");
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		stub(fixture);

		service.publish("monitor-1");

		assertThat(fixture.monitor().getPublishedUpstreamFeedbackUrl())
			.isEqualTo("https://8.8.4.4/override?callback=__CALLBACK__");
	}

	@Test
	void publishesTemplateGetRouteWithSgyzStyleMacros() {
		Fixture fixture = fixture();
		fixture.advertiser().setAdapterCode("TEMPLATE_GET");
		fixture.advertiser()
			.setBidUrl("https://8.8.8.8/feedback?request_id=__REQUEST_ID__&oaid=__OAID__&callback=__CALLBACK_URL__");
		fixture.advertiser().setMacroParams("__REQUEST_ID__,__CALLBACK_URL__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		stub(fixture);

		var result = service.publish("monitor-1");

		assertThat(result).containsEntry("routeStatus", "PUBLISHED");
	}

	@Test
	void rejectsUnknownTemplateGetMacro() {
		Fixture fixture = fixture();
		fixture.advertiser().setAdapterCode("TEMPLATE_GET");
		fixture.advertiser().setBidUrl("https://8.8.8.8/feedback?callback=__CALLBACK__&unknown=__UNKNOWN__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("__UNKNOWN__");
	}

	@Test
	void publishesCpcRouteWhenUnitPriceIsPositive() {
		Fixture fixture = fixture();
		fixture.monitor().setBillingMode("CPC");
		fixture.monitor().setUnitPrice(new BigDecimal("0.125000"));
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		fixture.advertiser().setBidUrl("https://8.8.8.8/click?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		stub(fixture);

		assertThat(service.publish("monitor-1")).containsEntry("routeStatus", "PUBLISHED");
	}

	@Test
	void rejectsCpcRouteWithoutPositiveUnitPrice() {
		Fixture fixture = fixture();
		fixture.monitor().setBillingMode("CPC");
		fixture.monitor().setUnitPrice(BigDecimal.ZERO);
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		fixture.advertiser().setBidUrl("https://8.8.8.8/click?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("单价必须大于 0");
	}

	@Test
	void validatesTemplateGetDownstreamCallbackMacros() {
		Fixture fixture = fixture();
		fixture.channel().setAdapterCode("TEMPLATE_GET_DOWNSTREAM");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback?event=__EVENT__&event_id=__EVENT_ID__");
		fixture.advertiser().setBidUrl("https://8.8.8.8/click?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		stub(fixture);

		assertThat(service.publish("monitor-1")).containsEntry("routeStatus", "PUBLISHED");
	}

	private void stub(Fixture fixture) {
		when(adapterService.requireEnabled(fixture.channel().getAdapterCode(), "DOWNSTREAM"))
			.thenReturn(downstream(fixture.channel().getAdapterCode()));
		when(adapterService.requireEnabled(fixture.advertiser().getAdapterCode(), "UPSTREAM"))
			.thenReturn(upstream(fixture.advertiser().getAdapterCode()));
		when(monitorMapper.selectOne(any())).thenReturn(fixture.monitor());
		when(channelMapper.selectById("channel-1")).thenReturn(fixture.channel());
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(fixture.advertiser());
	}

	private AdxAdapterService.PublishedAdapter upstream(String code) {
		if ("TEMPLATE_GET".equals(code)) {
			return new AdxAdapterService.PublishedAdapter(code, "UPSTREAM", "GET", "GET",
					Set.of("__REQUEST_ID__", "__OAID__", "__CALLBACK__", "__CALLBACK_URL__"), Set.of(),
					List.of(Set.of("__CALLBACK__", "__CALLBACK_URL__")));
		}
		return new AdxAdapterService.PublishedAdapter(code, "UPSTREAM", "GET", "GET", Set.of("__CALLBACK__"),
				Set.of("__CALLBACK__"), List.of());
	}

	private AdxAdapterService.PublishedAdapter downstream(String code) {
		if ("TEMPLATE_GET_DOWNSTREAM".equals(code)) {
			return new AdxAdapterService.PublishedAdapter(code, "DOWNSTREAM", "POST", "GET",
					Set.of("__EVENT__", "__EVENT_ID__"), Set.of("__EVENT__", "__EVENT_ID__"), List.of());
		}
		return new AdxAdapterService.PublishedAdapter(code, "DOWNSTREAM", "POST", "POST", Set.of(), Set.of(),
				List.of());
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
