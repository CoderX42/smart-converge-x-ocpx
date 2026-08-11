package com.yzytech.scx.ocpx.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxAppLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaCredential;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAppLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaCredentialMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

class OcpxRoutePublishServiceTest {

	private final OcpxMonitorLinkMapper monitorMapper = mock(OcpxMonitorLinkMapper.class);

	private final OcpxChannelMapper channelMapper = mock(OcpxChannelMapper.class);

	private final OcpxAdvertiserMapper advertiserMapper = mock(OcpxAdvertiserMapper.class);

	private final OcpxAppLinkMapper appLinkMapper = mock(OcpxAppLinkMapper.class);

	private final OcpxProductMapper productMapper = mock(OcpxProductMapper.class);

	private final OcpxMediaAccountMapper mediaAccountMapper = mock(OcpxMediaAccountMapper.class);

	private final OcpxMediaCredentialMapper mediaCredentialMapper = mock(OcpxMediaCredentialMapper.class);

	private final OcpxConfigChangeService configChangeService = mock(OcpxConfigChangeService.class);

	private final OcpxAdapterService adapterService = mock(OcpxAdapterService.class);

	private final OcpxEventService eventService = mock(OcpxEventService.class);

	private final OcpxAccessScope accessScope = mock(OcpxAccessScope.class);

	private final OcpxUrlPolicy urlPolicy = new OcpxUrlPolicy();

	private final OcpxRoutePublishService service = new OcpxRoutePublishService(monitorMapper, channelMapper,
			advertiserMapper, appLinkMapper, productMapper, mediaAccountMapper, mediaCredentialMapper,
			configChangeService, adapterService, eventService, accessScope, urlPolicy);

	@Test
	void blocksTaskBoundMonitorFromTheLegacyPublishPath() {
		Fixture fixture = fixture();
		fixture.monitor().setTaskId("task-1");
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).hasMessageContaining("REQUIRES_S7");
	}

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
		assertThat(fixture.monitor().getPublishedAdvertiserCredentialRevision()).isEqualTo(4L);
		assertThat(fixture.monitor().getPublishedUpstreamAdapterVersion()).isEqualTo(7L);
		assertThat(fixture.monitor().getPublishedDocumentId()).isEqualTo("document-2");
		assertThat(fixture.monitor().getPublishedDocumentRevision()).isEqualTo(2L);
		assertThat(fixture.monitor().getRepublishRequired()).isFalse();
		verify(monitorMapper).updateById(fixture.monitor());
		verify(configChangeService).enqueueMonitor("monitor-1", 5L, "ROUTE_PUBLISHED");
	}

	@Test
	void freezesLinkedProductAndEventPolicyRevisionsWithoutRuntimeLookup() {
		Fixture fixture = fixture();
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		fixture.advertiser().setBidUrl("https://8.8.8.8/click?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		fixture.product().setConfigVersion(6L);
		fixture.product().setEventPolicyRevision(3L);
		stub(fixture);

		service.publish("monitor-1");

		assertThat(fixture.monitor().getPublishedProductId()).isEqualTo("product-1");
		assertThat(fixture.monitor().getPublishedProductVersion()).isEqualTo(6L);
		assertThat(fixture.monitor().getPublishedProductEventPolicyRevision()).isEqualTo(3L);
		assertThat(fixture.monitor().getPublishedAppLinkId()).isEqualTo("app-link-1");
		assertThat(fixture.monitor().getPublishedAppLinkVersion()).isEqualTo(9L);
		assertThat(fixture.monitor().getPublishedAppLinkType()).isEqualTo("ANDROID");
		assertThat(fixture.monitor().getPublishedLinkUrlTemplate()).isEqualTo(fixture.appLink().getLinkUrlTemplate());
		assertThat(fixture.monitor().getPublishedLinkTemplateFingerprint())
			.isEqualTo(fixture.appLink().getTemplateFingerprint());
		assertThat(fixture.monitor().getPublishedRedirectEnabled()).isTrue();
	}

	@Test
	void rejectsLegacyAppLinkWithoutAnExplicitProductMapping() {
		Fixture fixture = fixture();
		fixture.appLink().setProductId(null);
		fixture.appLink().setMappingStatus("LEGACY_PENDING_PRODUCT");
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Product 映射");
	}

	@Test
	void freezesExplicitMediaAccountAndCredentialRevisions() {
		Fixture fixture = fixture();
		fixture.monitor().setMediaAccountId("media-1");
		fixture.monitor().setMediaCredentialId("credential-2");
		fixture.monitor().setDownstreamCallbackUrl("https://1.1.1.1/callback");
		fixture.channel().setCallbackHostAllowlist("1.1.1.1");
		fixture.advertiser().setBidUrl("https://8.8.8.8/click?callback=__CALLBACK__");
		fixture.advertiser().setUpstreamHostAllowlist("8.8.8.8");
		OcpxMediaAccount account = new OcpxMediaAccount();
		account.setId("media-1");
		account.setChannelId("channel-1");
		account.setStatus("ACTIVE");
		account.setConfigVersion(8L);
		account.setCredentialRevision(5L);
		OcpxMediaCredential credential = new OcpxMediaCredential();
		credential.setId("credential-2");
		credential.setMediaAccountId("media-1");
		credential.setStatus("ACTIVE");
		credential.setCurrentSecretRevision(3L);
		when(mediaAccountMapper.selectById("media-1")).thenReturn(account);
		when(mediaCredentialMapper.selectById("credential-2")).thenReturn(credential);
		stub(fixture);

		service.publish("monitor-1");

		assertThat(fixture.monitor().getPublishedMediaAccountId()).isEqualTo("media-1");
		assertThat(fixture.monitor().getPublishedMediaAccountVersion()).isEqualTo(8L);
		assertThat(fixture.monitor().getPublishedMediaAccountCredentialRevision()).isEqualTo(5L);
		assertThat(fixture.monitor().getPublishedMediaCredentialId()).isEqualTo("credential-2");
		assertThat(fixture.monitor().getPublishedMediaCredentialSecretRevision()).isEqualTo(3L);
		verify(accessScope).require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, "media-1",
				OcpxAccessScope.PermissionLevel.OPERATE);
	}

	@Test
	void rejectsMediaAccountFromAnotherChannel() {
		Fixture fixture = fixture();
		fixture.monitor().setMediaAccountId("media-1");
		OcpxMediaAccount account = new OcpxMediaAccount();
		account.setId("media-1");
		account.setChannelId("channel-other");
		account.setStatus("ACTIVE");
		when(mediaAccountMapper.selectById("media-1")).thenReturn(account);
		stub(fixture);

		assertThatThrownBy(() -> service.publish("monitor-1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("渠道不一致");
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
		when(appLinkMapper.selectById("app-link-1")).thenReturn(fixture.appLink());
		when(productMapper.selectById("product-1")).thenReturn(fixture.product());
	}

	private OcpxAdapterService.PublishedAdapter upstream(String code) {
		if ("TEMPLATE_GET".equals(code)) {
			return new OcpxAdapterService.PublishedAdapter(code, "UPSTREAM", "GET", "GET",
					Set.of("__REQUEST_ID__", "__OAID__", "__CALLBACK__", "__CALLBACK_URL__"), Set.of(),
					List.of(Set.of("__CALLBACK__", "__CALLBACK_URL__")));
		}
		return new OcpxAdapterService.PublishedAdapter(code, "UPSTREAM", "GET", "GET", Set.of("__CALLBACK__"),
				Set.of("__CALLBACK__"), List.of());
	}

	private OcpxAdapterService.PublishedAdapter downstream(String code) {
		if ("TEMPLATE_GET_DOWNSTREAM".equals(code)) {
			return new OcpxAdapterService.PublishedAdapter(code, "DOWNSTREAM", "POST", "GET",
					Set.of("__EVENT__", "__EVENT_ID__"), Set.of("__EVENT__", "__EVENT_ID__"), List.of());
		}
		return new OcpxAdapterService.PublishedAdapter(code, "DOWNSTREAM", "POST", "POST", Set.of(), Set.of(),
				List.of());
	}

	private Fixture fixture() {
		OcpxMonitorLink monitor = new OcpxMonitorLink();
		monitor.setId("monitor-1");
		monitor.setChannelId("channel-1");
		monitor.setAdvertiserId("advertiser-1");
		monitor.setAppLinkId("app-link-1");
		monitor.setRtaMode("OFF");
		monitor.setBillingMode("OFF");
		OcpxChannel channel = new OcpxChannel();
		channel.setId("channel-1");
		channel.setAdapterCode("STANDARD");
		channel.setStatus("ACTIVE");
		channel.setAuthType("HMAC");
		channel.setSecretKey("12345678901234567890123456789012");
		channel.setCallbackHostAllowlist("callback.example");
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-1");
		advertiser.setAdapterCode("COCO");
		advertiser.setAdapterVersion(7L);
		advertiser.setCredentialRevision(4L);
		advertiser.setCurrentDocumentId("document-2");
		advertiser.setDocumentRevision(2L);
		advertiser.setStatus("ACTIVE");
		advertiser.setBidUrl("https://coco.example/click?callback=__CALLBACK__");
		advertiser.setUpstreamHostAllowlist("coco.example");
		OcpxUrlPolicy.Result checked = urlPolicy.validate("https://8.8.4.4/link?clickid=__CLICKID__");
		OcpxAppLink appLink = new OcpxAppLink();
		appLink.setId("app-link-1");
		appLink.setAdvertiserId("advertiser-1");
		appLink.setProductId("product-1");
		appLink.setMappingStatus("READY");
		appLink.setStatus("ACTIVE");
		appLink.setLinkType("ANDROID");
		appLink.setConfigVersion(9L);
		appLink.setLinkUrlTemplate(checked.normalizedUrl());
		appLink.setTemplateHost(checked.host());
		appLink.setTemplateFingerprint(checked.fingerprint());
		appLink.setTemplateMacroSet("[\"__CLICKID__\"]");
		appLink.setRedirectEnabled(true);
		appLink.setDeeplinkUrl("https://8.8.4.4/open");
		appLink.setDownloadUrl("https://8.8.4.4/download");
		OcpxProduct product = new OcpxProduct();
		product.setId("product-1");
		product.setAdvertiserId("advertiser-1");
		product.setStatus("ACTIVE");
		product.setConfigVersion(5L);
		product.setEventPolicyRevision(2L);
		return new Fixture(monitor, channel, advertiser, appLink, product);
	}

	private record Fixture(OcpxMonitorLink monitor, OcpxChannel channel, OcpxAdvertiser advertiser, OcpxAppLink appLink,
			OcpxProduct product) {
	}

}
