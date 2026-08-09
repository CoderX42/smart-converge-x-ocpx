package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.dto.CatalogRequests;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAppLink;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxDeductionRuleMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserMapper;
import com.yzytech.scx.adx.mapper.AdxSystemUserRoleMapper;
import com.yzytech.scx.system.api.entity.SysUser;
import com.yzytech.scx.system.api.entity.SysUserRole;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AdxCatalogServiceTest {

	private final AdxChannelMapper channelMapper = mock(AdxChannelMapper.class);

	private final AdxAdvertiserMapper advertiserMapper = mock(AdxAdvertiserMapper.class);

	private final AdxAppLinkMapper appLinkMapper = mock(AdxAppLinkMapper.class);

	private final AdxMonitorLinkMapper monitorMapper = mock(AdxMonitorLinkMapper.class);

	private final AdxDeductionRuleMapper deductionMapper = mock(AdxDeductionRuleMapper.class);

	private final AdxSystemUserMapper systemUserMapper = mock(AdxSystemUserMapper.class);

	private final AdxSystemUserRoleMapper systemUserRoleMapper = mock(AdxSystemUserRoleMapper.class);

	private final AdxAccessScope accessScope = mock(AdxAccessScope.class);

	private final AdxConfigChangeService configChangeService = mock(AdxConfigChangeService.class);

	private final AdxEventService eventService = mock(AdxEventService.class);

	private AdxCatalogService service;

	@BeforeEach
	void setUp() {
		service = new AdxCatalogService(channelMapper, advertiserMapper, appLinkMapper, monitorMapper, deductionMapper,
				systemUserMapper, systemUserRoleMapper, accessScope, configChangeService, eventService);
	}

	@Test
	void createAdvertiserRequiresPassword() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		CatalogRequests.SaveAdvertiser request = new CatalogRequests.SaveAdvertiser("测试广告主", "COCO",
				"https://example.test/bid", null, null, "example.test", "advertiser", null, "ACTIVE");

		assertThatThrownBy(() -> service.saveAdvertiser(null, request)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("新增广告主时密码必填");
	}

	@Test
	void createAdvertiserAlsoCreatesAScopedLogin() {
		when(advertiserMapper.selectCount(any())).thenReturn(0L);
		when(systemUserMapper.selectCount(any())).thenReturn(0L);
		when(systemUserRoleMapper.selectCount(any())).thenReturn(0L);
		when(advertiserMapper.insert(any(AdxAdvertiser.class))).thenAnswer(invocation -> {
			((AdxAdvertiser) invocation.getArgument(0)).setId("advertiser-1");
			return 1;
		});
		when(systemUserMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
			((SysUser) invocation.getArgument(0)).setId("user-1");
			return 1;
		});
		CatalogRequests.SaveAdvertiser request = new CatalogRequests.SaveAdvertiser("测试广告主", "COCO",
				"https://example.test/bid", null, null, "example.test", "advertiser", "password-123", "ACTIVE");

		service.saveAdvertiser(null, request);

		ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
		verify(systemUserMapper).insert(user.capture());
		assertThat(user.getValue().getUsername()).isEqualTo("advertiser");
		assertThat(user.getValue().getShopId()).isEqualTo("advertiser-1");
		assertThat(user.getValue().getType()).isEqualTo("2");
		assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("password-123",
				user.getValue().getPassword()))
			.isTrue();
		ArgumentCaptor<SysUserRole> role = ArgumentCaptor.forClass(SysUserRole.class);
		verify(systemUserRoleMapper).insert(role.capture());
		assertThat(role.getValue().getRoleId()).isEqualTo("role_advertiser");
	}

	@Test
	void monitorRejectsAppLinkOwnedByAnotherAdvertiser() {
		AdxChannel channel = new AdxChannel();
		channel.setId("channel-1");
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		AdxAppLink link = new AdxAppLink();
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
		AdxChannel channel = new AdxChannel();
		channel.setId("channel-1");
		channel.setChannelCode("media_main");
		channel.setUri("https://ocpx.example/ocpx/");
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		AdxAppLink link = new AdxAppLink();
		link.setId("link-1");
		link.setAdvertiserId("advertiser-1");
		when(channelMapper.selectById("channel-1")).thenReturn(channel);
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(appLinkMapper.selectById("link-1")).thenReturn(link);
		when(monitorMapper.insert(any(com.yzytech.scx.adx.entity.AdxMonitorLink.class))).thenAnswer(invocation -> {
			((com.yzytech.scx.adx.entity.AdxMonitorLink) invocation.getArgument(0)).setId("monitor-1");
			return 1;
		});
		CatalogRequests.SaveMonitor request = new CatalogRequests.SaveMonitor("channel-1", "advertiser-1", "link-1",
				"监测链接", null, BigDecimal.ONE, null, null, null, null, "OFF", "OFF");

		service.saveMonitor(null, request);

		ArgumentCaptor<com.yzytech.scx.adx.entity.AdxMonitorLink> monitor = ArgumentCaptor
			.forClass(com.yzytech.scx.adx.entity.AdxMonitorLink.class);
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

	@Test
	void deletingAdvertiserUsesOriginalRbacLogicalUserDeletion() {
		AdxAdvertiser advertiser = new AdxAdvertiser();
		advertiser.setId("advertiser-1");
		SysUser user = new SysUser();
		user.setId("user-1");
		when(advertiserMapper.selectById("advertiser-1")).thenReturn(advertiser);
		when(systemUserMapper.selectOne(any())).thenReturn(user);

		service.deleteAdvertiser("advertiser-1");

		verify(advertiserMapper).deleteById("advertiser-1");
		verify(systemUserRoleMapper).delete(any());
		verify(systemUserMapper).deleteById("user-1");
	}

}
