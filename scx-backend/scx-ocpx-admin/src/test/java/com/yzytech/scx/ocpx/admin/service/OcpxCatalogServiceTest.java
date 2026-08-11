package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.CatalogRequests;
import com.yzytech.scx.ocpx.admin.dto.CatalogViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxDeductionRuleMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxCatalogServiceTest {

	private final OcpxChannelMapper channelMapper = mock(OcpxChannelMapper.class);

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxAppLinkMapper appLinkMapper = mock(OcpxAppLinkMapper.class);

	private final OcpxMonitorLinkMapper monitorMapper = mock(OcpxMonitorLinkMapper.class);

	private final OcpxDeductionRuleMapper deductionMapper = mock(OcpxDeductionRuleMapper.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxConfigChangeService configChangeService = mock(OcpxConfigChangeService.class);

	private final OcpxEventService eventService = mock(OcpxEventService.class);

	private OcpxCatalogService service;

	@BeforeEach
	void setUp() {
		service = new OcpxCatalogService(channelMapper, advertiserMapper, appLinkMapper, monitorMapper, deductionMapper,
				accessScope, configChangeService, eventService, new OcpxUrlPolicy());
	}

	@Test
	void legacyCatalogProjectionNeverReturnsStoredFullUrls() {
		OcpxAppLink link = new OcpxAppLink();
		link.setId("link-1");
		link.setAdvertiserId("advertiser-1");
		link.setLinkName("历史链接");
		link.setLinkUrl("https://8.8.4.4/private-path?token=not-returned");
		link.setDeeplink("https://8.8.4.4/deep/private");
		link.setMappingStatus("LEGACY_PENDING_PRODUCT");

		CatalogViews.AppLink view = ReflectionTestUtils.invokeMethod(service, "appLinkView", link, "Advertiser");

		assertThat(view).isNotNull();
		assertThat(view.linkUrl()).isEqualTo("历史地址（待重新录入并校验）");
		assertThat(view.deeplink()).isEqualTo("历史地址（待重新录入并校验）");
		assertThat(view.toString()).doesNotContain("private-path", "not-returned", "/deep/private");
	}

	@Test
	void monitorRejectsAppLinkOwnedByAnotherAdvertiser() {
		OcpxChannel channel = new OcpxChannel();
		channel.setId("channel-1");
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-1");
		OcpxAppLink link = new OcpxAppLink();
		link.setId("link-1");
		link.setAdvertiserId("advertiser-2");
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(appLinkMapper.selectById("link-1")).thenReturn(link);
		CatalogRequests.SaveMonitor request = new CatalogRequests.SaveMonitor("channel-1", "advertiser-1", "link-1",
				"监测链接", null, BigDecimal.ONE, null, null, null, null, "OFF", "OFF");

		assertThatThrownBy(() -> service.saveMonitor(null, request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("应用链接不属于所选广告主");
	}

	@Test
	void monitorPublishesTheV70SignedPostEndpointInsteadOfLegacyQueryLinks() {
		OcpxChannel channel = new OcpxChannel();
		channel.setId("channel-1");
		channel.setChannelCode("media_main");
		channel.setUri("https://ocpx.example/ocpx/");
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-1");
		OcpxAppLink link = new OcpxAppLink();
		link.setId("link-1");
		link.setAdvertiserId("advertiser-1");
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(appLinkMapper.selectById("link-1")).thenReturn(link);
		when(monitorMapper.insert(any(com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink.class))).thenAnswer(invocation -> {
			((com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink) invocation.getArgument(0)).setId("monitor-1");
			return 1;
		});
		CatalogRequests.SaveMonitor request = new CatalogRequests.SaveMonitor("channel-1", "advertiser-1", "link-1",
				"监测链接", null, BigDecimal.ONE, null, null, null, null, "OFF", "OFF");

		service.saveMonitor(null, request);

		ArgumentCaptor<com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink> monitor = ArgumentCaptor
			.forClass(com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink.class);
		verify(monitorMapper).updateById(monitor.capture());
		assertThat(monitor.getValue().getShowUrl())
			.isEqualTo("https://ocpx.example/ocpx/open-api/ocpx/v1/channels/media_main/feedback");
		assertThat(monitor.getValue().getClickUrl()).isEqualTo(monitor.getValue().getShowUrl());
		assertThat(monitor.getValue().getClickDedupWindowSeconds()).isEqualTo(3600);
	}

	@Test
	void channelRejectsOcpxEngineBaseAddressWithQueryParameters() {
		CatalogRequests.SaveChannel request = new CatalogRequests.SaveChannel("media_main", "主渠道", "STANDARD", "HMAC",
				1, "https://ocpx.example/ocpx?token=browser-secret", "channel-secret", "event,callback", "ocpx.example",
				"ACTIVE");

		assertThatThrownBy(() -> service.saveChannel(null, request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query");
	}

}
