package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxAppLink;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.entity.AdxMediaAccount;
import com.yzytech.scx.adx.entity.AdxMediaCredential;
import com.yzytech.scx.adx.entity.AdxProduct;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxAppLinkMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import com.yzytech.scx.adx.mapper.AdxMediaAccountMapper;
import com.yzytech.scx.adx.mapper.AdxMediaCredentialMapper;
import com.yzytech.scx.adx.mapper.AdxProductMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdxRoutePublishService {

	private static final Pattern TEMPLATE_MACRO = Pattern.compile("__[A-Z0-9_]+__");

	private static final int DEFAULT_CLICK_DEDUP_WINDOW_SECONDS = 3600;

	private final AdxMonitorLinkMapper monitorMapper;

	private final AdxChannelMapper channelMapper;

	private final AdxAdvertiserMapper advertiserMapper;

	private final AdxAppLinkMapper appLinkMapper;

	private final AdxProductMapper productMapper;

	private final AdxMediaAccountMapper mediaAccountMapper;

	private final AdxMediaCredentialMapper mediaCredentialMapper;

	private final AdxConfigChangeService configChangeService;

	private final AdxAdapterService adapterService;

	private final AdxEventService eventService;

	private final AdxAccessScope accessScope;

	private final AdxUrlPolicy urlPolicy;

	public AdxRoutePublishService(AdxMonitorLinkMapper monitorMapper, AdxChannelMapper channelMapper,
			AdxAdvertiserMapper advertiserMapper, AdxAppLinkMapper appLinkMapper, AdxProductMapper productMapper,
			AdxMediaAccountMapper mediaAccountMapper, AdxMediaCredentialMapper mediaCredentialMapper,
			AdxConfigChangeService configChangeService, AdxAdapterService adapterService, AdxEventService eventService,
			AdxAccessScope accessScope, AdxUrlPolicy urlPolicy) {
		this.monitorMapper = monitorMapper;
		this.channelMapper = channelMapper;
		this.advertiserMapper = advertiserMapper;
		this.appLinkMapper = appLinkMapper;
		this.productMapper = productMapper;
		this.mediaAccountMapper = mediaAccountMapper;
		this.mediaCredentialMapper = mediaCredentialMapper;
		this.configChangeService = configChangeService;
		this.adapterService = adapterService;
		this.eventService = eventService;
		this.accessScope = accessScope;
		this.urlPolicy = urlPolicy;
	}

	@Transactional
	public Map<String, Object> publish(String monitorId) {
		AdxMonitorLink monitor = require(
				monitorMapper.selectOne(
						Wrappers.<AdxMonitorLink>lambdaQuery().eq(AdxMonitorLink::getId, monitorId).last("FOR UPDATE")),
				"监测链接不存在");
		accessScope.require(AdxAccessScope.ScopeType.ADVERTISER, monitor.getAdvertiserId(),
				AdxAccessScope.PermissionLevel.OPERATE);
		accessScope.require(AdxAccessScope.ScopeType.CHANNEL, monitor.getChannelId(),
				AdxAccessScope.PermissionLevel.OPERATE);
		if (StringUtils.hasText(monitor.getTaskId())) {
			throw new BootBusinessException(HttpStatus.CONFLICT.value(),
					"ADX_TASK_ROUTE_REQUIRES_S7：Task 路由必须使用发布检查和事务发布接口");
		}
		AdxChannel channel = require(channelMapper.selectById(monitor.getChannelId()), "渠道不存在");
		AdxAdvertiser advertiser = require(advertiserMapper.selectById(monitor.getAdvertiserId()), "广告主不存在");
		AppLinkContext appLink = linkedAppLink(monitor);
		AdxProduct product = appLink.product();
		MediaContext media = linkedMedia(monitor);
		if (monitor.getClickDedupWindowSeconds() == null) {
			monitor.setClickDedupWindowSeconds(DEFAULT_CLICK_DEDUP_WINDOW_SECONDS);
		}
		validate(channel, advertiser, monitor);
		long nextVersion = monitor.getConfigVersion() == null ? 1 : monitor.getConfigVersion() + 1;
		int eventCount = eventService.publishSnapshot(monitorId, monitor.getChannelId(), nextVersion);
		monitor.setRouteStatus("PUBLISHED");
		monitor.setConfigVersion(nextVersion);
		monitor.setPublishedAt(LocalDateTime.now());
		monitor.setRepublishRequired(false);
		monitor.setRepublishReason(null);
		monitor.setEventSnapshotVersion(1);
		monitor.setEventMappingMode("LEGACY_V80_SNAPSHOT");
		monitor.setPublishedUpstreamFeedbackUrl(StringUtils.hasText(monitor.getUpstreamFeedbackUrl())
				? monitor.getUpstreamFeedbackUrl() : advertiser.getBidUrl());
		monitor.setPublishedAdvertiserCredentialRevision(
				advertiser.getCredentialRevision() == null ? 0 : advertiser.getCredentialRevision());
		monitor.setPublishedUpstreamAdapterVersion(
				advertiser.getAdapterVersion() == null ? 1 : advertiser.getAdapterVersion());
		monitor.setPublishedDocumentId(advertiser.getCurrentDocumentId());
		monitor.setPublishedDocumentRevision(
				advertiser.getDocumentRevision() == null ? 0 : advertiser.getDocumentRevision());
		monitor.setPublishedProductId(product == null ? null : product.getId());
		monitor.setPublishedProductVersion(
				product == null || product.getConfigVersion() == null ? 0 : product.getConfigVersion());
		monitor.setPublishedProductEventPolicyRevision(
				product.getEventPolicyRevision() == null ? 0 : product.getEventPolicyRevision());
		monitor.setPublishedAppLinkId(appLink.link().getId());
		monitor.setPublishedAppLinkVersion(number(appLink.link().getConfigVersion()));
		monitor.setPublishedAppLinkType(appLink.link().getLinkType());
		monitor.setPublishedLinkUrlTemplate(appLink.link().getLinkUrlTemplate());
		monitor.setPublishedLinkTemplateHost(appLink.link().getTemplateHost());
		monitor.setPublishedLinkTemplateFingerprint(appLink.link().getTemplateFingerprint());
		monitor.setPublishedLinkTemplateMacroSet(appLink.link().getTemplateMacroSet());
		monitor.setPublishedRedirectEnabled(Boolean.TRUE.equals(appLink.link().getRedirectEnabled()));
		monitor.setPublishedDeeplinkUrl(appLink.link().getDeeplinkUrl());
		monitor.setPublishedDownloadUrl(appLink.link().getDownloadUrl());
		monitor.setPublishedMediaAccountId(media == null ? null : media.account().getId());
		monitor.setPublishedMediaAccountVersion(media == null ? 0 : number(media.account().getConfigVersion()));
		monitor.setPublishedMediaAccountCredentialRevision(
				media == null ? 0 : number(media.account().getCredentialRevision()));
		monitor.setPublishedMediaCredentialId(media == null ? null : media.credential().getId());
		monitor.setPublishedMediaCredentialSecretRevision(
				media == null ? 0 : number(media.credential().getCurrentSecretRevision()));
		monitorMapper.updateById(monitor);
		configChangeService.enqueueMonitor(monitorId, nextVersion, "ROUTE_PUBLISHED");
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", monitorId);
		result.put("routeStatus", monitor.getRouteStatus());
		result.put("configVersion", nextVersion);
		result.put("publishedAt", monitor.getPublishedAt());
		result.put("eventBindingCount", eventCount);
		return result;
	}

	private MediaContext linkedMedia(AdxMonitorLink monitor) {
		if (!StringUtils.hasText(monitor.getMediaAccountId())) {
			return null;
		}
		accessScope.require(AdxAccessScope.ScopeType.MEDIA_ACCOUNT, monitor.getMediaAccountId(),
				AdxAccessScope.PermissionLevel.OPERATE);
		AdxMediaAccount account = require(mediaAccountMapper.selectById(monitor.getMediaAccountId()), "媒体账号不存在");
		if (!monitor.getChannelId().equals(account.getChannelId()) || !"ACTIVE".equals(account.getStatus())) {
			throw new IllegalArgumentException("媒体账号未启用或与渠道不一致");
		}
		String credentialId = StringUtils.hasText(monitor.getMediaCredentialId()) ? monitor.getMediaCredentialId()
				: account.getDefaultCredentialId();
		if (!StringUtils.hasText(credentialId)) {
			throw new IllegalArgumentException("媒体账号未配置默认凭据");
		}
		AdxMediaCredential credential = require(mediaCredentialMapper.selectById(credentialId), "媒体凭据不存在");
		if (!account.getId().equals(credential.getMediaAccountId()) || !"ACTIVE".equals(credential.getStatus())) {
			throw new IllegalArgumentException("媒体凭据未启用或不属于该媒体账号");
		}
		return new MediaContext(account, credential);
	}

	private AppLinkContext linkedAppLink(AdxMonitorLink monitor) {
		if (!StringUtils.hasText(monitor.getAppLinkId())) {
			throw new IllegalArgumentException("发布路由必须选择应用链接");
		}
		AdxAppLink appLink = require(appLinkMapper.selectById(monitor.getAppLinkId()), "应用链接不存在");
		if (!monitor.getAdvertiserId().equals(appLink.getAdvertiserId())) {
			throw new IllegalArgumentException("应用链接与广告主不一致");
		}
		if (!"READY".equals(appLink.getMappingStatus()) || !"ACTIVE".equals(appLink.getStatus())
				|| !StringUtils.hasText(appLink.getProductId())) {
			throw new IllegalArgumentException("应用链接尚未完成 Product 映射或未启用");
		}
		AdxProduct product = require(productMapper.selectById(appLink.getProductId()), "产品不存在");
		if (!monitor.getAdvertiserId().equals(product.getAdvertiserId()) || !"ACTIVE".equals(product.getStatus())) {
			throw new IllegalArgumentException("产品未启用或与广告主不一致");
		}
		AdxUrlPolicy.Result checkedUrl = urlPolicy.validate(appLink.getLinkUrlTemplate());
		if (!"PASS".equals(checkedUrl.status()) || !checkedUrl.host().equals(appLink.getTemplateHost())
				|| !checkedUrl.fingerprint().equals(appLink.getTemplateFingerprint())) {
			throw new IllegalArgumentException("应用链接 URL 元数据已失配，请先重新保存链接");
		}
		if (appLink.getConfigVersion() == null || appLink.getConfigVersion() < 1) {
			throw new IllegalArgumentException("应用链接配置版本无效");
		}
		return new AppLinkContext(appLink, product);
	}

	private void validate(AdxChannel channel, AdxAdvertiser advertiser, AdxMonitorLink monitor) {
		if (!"ACTIVE".equals(channel.getStatus()) || !"ACTIVE".equals(advertiser.getStatus())) {
			throw new IllegalArgumentException("渠道和广告主必须处于启用状态");
		}
		AdxAdapterService.PublishedAdapter downstreamAdapter = adapterService.requireEnabled(channel.getAdapterCode(),
				"DOWNSTREAM");
		AdxAdapterService.PublishedAdapter upstreamAdapter = adapterService.requireEnabled(advertiser.getAdapterCode(),
				"UPSTREAM");
		if (!"HMAC".equals(channel.getAuthType())) {
			throw new IllegalArgumentException("发布路由必须使用 HMAC 鉴权");
		}
		if (!StringUtils.hasText(channel.getSecretKey()) || channel.getSecretKey().length() < 32) {
			throw new IllegalArgumentException("HMAC 渠道必须配置至少 32 位密钥");
		}
		String effectiveUpstreamUrl = StringUtils.hasText(monitor.getUpstreamFeedbackUrl())
				? monitor.getUpstreamFeedbackUrl() : advertiser.getBidUrl();
		validateAdapterMacros(effectiveUpstreamUrl, advertiser.getMacroParams(), upstreamAdapter, "上游请求地址");
		validateHttps(effectiveUpstreamUrl, advertiser.getUpstreamHostAllowlist(), upstreamAdapter, "上游请求地址");
		if (!StringUtils.hasText(monitor.getDownstreamCallbackUrl())) {
			throw new IllegalArgumentException("发布路由必须配置下游回调地址");
		}
		validateAdapterMacros(monitor.getDownstreamCallbackUrl(), null, downstreamAdapter, "下游回调地址");
		validateHttps(monitor.getDownstreamCallbackUrl(), channel.getCallbackHostAllowlist(), downstreamAdapter,
				"下游回调地址");
		if (!"OFF".equals(monitor.getRtaMode())) {
			throw new IllegalArgumentException("首期 RTA 只能为 OFF");
		}
		if (!("OFF".equals(monitor.getBillingMode()) || "AUDIT_ONLY".equals(monitor.getBillingMode())
				|| "CPC".equals(monitor.getBillingMode()))) {
			throw new IllegalArgumentException("计费模式只能为 OFF、AUDIT_ONLY 或 CPC");
		}
		if (monitor.getClickDedupWindowSeconds() < 0 || monitor.getClickDedupWindowSeconds() > 604800) {
			throw new IllegalArgumentException("点击验重窗口必须在 0 到 604800 秒之间");
		}
		if (!"OFF".equals(monitor.getBillingMode())
				&& (monitor.getUnitPrice() == null || monitor.getUnitPrice().signum() <= 0)) {
			throw new IllegalArgumentException("启用点击审计或 CPC 计费时单价必须大于 0");
		}
	}

	/**
	 * 按数据库中发布的 Adapter 能力校验 URL，不在通用发布服务中识别任何具体媒体编码。
	 */
	private void validateAdapterMacros(String url, String configuredRequiredMacros,
			AdxAdapterService.PublishedAdapter adapter, String label) {
		Set<String> supportedMacros = adapter.supportedMacros();
		Matcher matcher = TEMPLATE_MACRO.matcher(url);
		while (matcher.find()) {
			if (!supportedMacros.contains(matcher.group())) {
				throw new IllegalArgumentException(label + "包含当前适配器不支持的宏：" + matcher.group());
			}
		}
		for (String required : adapter.requiredUrlMacros()) {
			if (!url.contains(required)) {
				throw new IllegalArgumentException(label + "缺少适配器必需宏：" + required);
			}
		}
		for (Set<String> alternatives : adapter.requiredAnyUrlMacros()) {
			if (alternatives.stream().noneMatch(url::contains)) {
				throw new IllegalArgumentException(label + "至少需要一个回调宏：" + String.join(" 或 ", alternatives));
			}
		}
		if (!StringUtils.hasText(configuredRequiredMacros)) {
			return;
		}
		for (String required : configuredRequiredMacros.split("[,\\s]+")) {
			if (!supportedMacros.contains(required)) {
				throw new IllegalArgumentException(label + "必填宏配置无效：" + required);
			}
			if (!url.contains(required)) {
				throw new IllegalArgumentException(label + "缺少已声明的必填宏：" + required);
			}
		}
	}

	private void validateHttps(String value, String allowlist, AdxAdapterService.PublishedAdapter adapter,
			String label) {
		try {
			URI uri = URI.create(value);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
					|| uri.getUserInfo() != null) {
				throw new IllegalArgumentException(label + "必须是有效 HTTPS 地址");
			}
			String host = uri.getHost().toLowerCase();
			boolean allowed = StringUtils.hasText(allowlist)
					&& Arrays.stream(allowlist.split("[,\\s]+")).map(String::trim).anyMatch(host::equalsIgnoreCase);
			if (!allowed) {
				throw new IllegalArgumentException(label + "域名不在白名单");
			}
			AdxUrlPolicy.Result result = urlPolicy.validate(value, adapter.supportedMacros());
			if (!"PASS".equals(result.status())) {
				throw new IllegalArgumentException(label + "未通过无网络 URL 安全校验（私网/保留地址/协议/语法）");
			}
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalArgumentException(label + "URL 安全校验失败");
		}
	}

	private <T> T require(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private long number(Long value) {
		return value == null ? 0 : value;
	}

	private record MediaContext(AdxMediaAccount account, AdxMediaCredential credential) {
	}

	private record AppLinkContext(AdxAppLink link, AdxProduct product) {
	}

}
