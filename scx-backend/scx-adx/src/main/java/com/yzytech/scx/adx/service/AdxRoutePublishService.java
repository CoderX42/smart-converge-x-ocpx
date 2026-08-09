package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.entity.AdxAdvertiser;
import com.yzytech.scx.adx.entity.AdxChannel;
import com.yzytech.scx.adx.entity.AdxMonitorLink;
import com.yzytech.scx.adx.mapper.AdxAdvertiserMapper;
import com.yzytech.scx.adx.mapper.AdxChannelMapper;
import com.yzytech.scx.adx.mapper.AdxMonitorLinkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
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

	private final AdxConfigChangeService configChangeService;

	private final AdxAdapterService adapterService;

	public AdxRoutePublishService(AdxMonitorLinkMapper monitorMapper, AdxChannelMapper channelMapper,
			AdxAdvertiserMapper advertiserMapper, AdxConfigChangeService configChangeService,
			AdxAdapterService adapterService) {
		this.monitorMapper = monitorMapper;
		this.channelMapper = channelMapper;
		this.advertiserMapper = advertiserMapper;
		this.configChangeService = configChangeService;
		this.adapterService = adapterService;
	}

	@Transactional
	public Map<String, Object> publish(String monitorId) {
		AdxMonitorLink monitor = require(monitorMapper.selectById(monitorId), "监测链接不存在");
		AdxChannel channel = require(channelMapper.selectById(monitor.getChannelId()), "渠道不存在");
		AdxAdvertiser advertiser = require(advertiserMapper.selectById(monitor.getAdvertiserId()), "广告主不存在");
		if (monitor.getClickDedupWindowSeconds() == null) {
			monitor.setClickDedupWindowSeconds(DEFAULT_CLICK_DEDUP_WINDOW_SECONDS);
		}
		validate(channel, advertiser, monitor);
		long nextVersion = monitor.getConfigVersion() == null ? 1 : monitor.getConfigVersion() + 1;
		monitor.setRouteStatus("PUBLISHED");
		monitor.setConfigVersion(nextVersion);
		monitor.setPublishedAt(LocalDateTime.now());
		monitor.setPublishedUpstreamFeedbackUrl(StringUtils.hasText(monitor.getUpstreamFeedbackUrl())
				? monitor.getUpstreamFeedbackUrl() : advertiser.getBidUrl());
		monitorMapper.updateById(monitor);
		configChangeService.enqueueMonitor(monitorId, nextVersion, "ROUTE_PUBLISHED");
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", monitorId);
		result.put("routeStatus", monitor.getRouteStatus());
		result.put("configVersion", nextVersion);
		result.put("publishedAt", monitor.getPublishedAt());
		return result;
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
		validateHttps(effectiveUpstreamUrl, advertiser.getUpstreamHostAllowlist(), "上游请求地址");
		validateAdapterMacros(effectiveUpstreamUrl, advertiser.getMacroParams(), upstreamAdapter, "上游请求地址");
		if (!StringUtils.hasText(monitor.getDownstreamCallbackUrl())) {
			throw new IllegalArgumentException("发布路由必须配置下游回调地址");
		}
		validateHttps(monitor.getDownstreamCallbackUrl(), channel.getCallbackHostAllowlist(), "下游回调地址");
		validateAdapterMacros(monitor.getDownstreamCallbackUrl(), null, downstreamAdapter, "下游回调地址");
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

	private void validateHttps(String value, String allowlist, String label) {
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
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
						|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
					throw new IllegalArgumentException(label + "域名解析到私网地址");
				}
			}
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalArgumentException(label + "域名无法解析");
		}
	}

	private <T> T require(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

}
