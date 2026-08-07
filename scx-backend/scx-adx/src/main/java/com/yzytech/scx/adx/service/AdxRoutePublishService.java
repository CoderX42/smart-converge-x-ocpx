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

	private static final Set<String> COCO_MACROS = Set.of("__IDFA__", "__IDFA_MD5__", "__IMEI__", "__IMEI_MD5__",
			"__OAID__", "__OAID_MD5__", "__IP__", "__CAID_LIST__", "__ANDROID_ID__", "__ANDROID_ID_MD5__", "__AAID__",
			"__UA__", "__OS__", "__MODEL__", "__CALLBACK__", "__CLICKID__", "__CREATIVEID__");

	private static final Set<String> TEMPLATE_GET_MACROS = Set.of("__EVENT__", "__EVENT_TIME__", "__EVENT_ID__",
			"__TS__", "__MTS__", "__REQUEST_ID__", "__CHANNEL_ID__", "__DSP_ID__", "__LINK_ID__", "__TASK_ID__",
			"__MONITOR_ID__", "__BIZ_ID__", "__CAMPAIGN_ID__", "__ADGROUP_ID__", "__CREATIVE_ID__", "__CREATIVEID__",
			"__CLICKID__", "__IDFA__", "__IDFA_MD5__", "__IDFA__MD5__", "__IMEI__", "__IMEI_MD5__", "__IMEI__MD5__",
			"__OAID__", "__OAID_MD5__", "__OAID__MD5__", "__CAID__", "__CAID_LIST__", "__ANDROID_ID__",
			"__ANDROID_ID_MD5__", "__AAID__", "__GAID__", "__IDFV__", "__IP__", "__UA__", "__OS__", "__MODEL__",
			"__CALLBACK__", "__CALLBACK_URL__");

	private static final Set<String> TEMPLATE_GET_DOWNSTREAM_MACROS = Set.of("__EVENT__", "__EVENT_ID__",
			"__SOURCE_EVENT_ID__", "__CLICKID__", "__MONITOR_ID__", "__CHANNEL_ID__", "__AMOUNT__", "__AMOUNT_RAW__",
			"__TS__");

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
		validate(channel, advertiser, monitor);
		long nextVersion = monitor.getConfigVersion() == null ? 1 : monitor.getConfigVersion() + 1;
		monitor.setRouteStatus("PUBLISHED");
		monitor.setConfigVersion(nextVersion);
		monitor.setPublishedAt(LocalDateTime.now());
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
		adapterService.requireEnabled(channel.getAdapterCode(), "DOWNSTREAM");
		adapterService.requireEnabled(advertiser.getAdapterCode(), "UPSTREAM");
		if (!"HMAC".equals(channel.getAuthType())) {
			throw new IllegalArgumentException("发布路由必须使用 HMAC 鉴权");
		}
		if (!StringUtils.hasText(channel.getSecretKey()) || channel.getSecretKey().length() < 32) {
			throw new IllegalArgumentException("HMAC 渠道必须配置至少 32 位密钥");
		}
		validateHttps(advertiser.getBidUrl(), advertiser.getUpstreamHostAllowlist(), "上游请求地址");
		if ("COCO".equals(advertiser.getAdapterCode())) {
			if (!advertiser.getBidUrl().contains("__CALLBACK__")) {
				throw new IllegalArgumentException("Coco 请求地址必须包含 __CALLBACK__ 宏");
			}
			validateMacros(advertiser, COCO_MACROS, "Coco");
		}
		if ("TEMPLATE_GET".equals(advertiser.getAdapterCode())) {
			if (!(advertiser.getBidUrl().contains("__CALLBACK__")
					|| advertiser.getBidUrl().contains("__CALLBACK_URL__"))) {
				throw new IllegalArgumentException("模板 GET 请求地址必须包含回调宏");
			}
			validateMacros(advertiser, TEMPLATE_GET_MACROS, "模板 GET");
		}
		if (!StringUtils.hasText(monitor.getDownstreamCallbackUrl())) {
			throw new IllegalArgumentException("发布路由必须配置下游回调地址");
		}
		validateHttps(monitor.getDownstreamCallbackUrl(), channel.getCallbackHostAllowlist(), "下游回调地址");
		if ("TEMPLATE_GET_DOWNSTREAM".equals(channel.getAdapterCode())) {
			if (!monitor.getDownstreamCallbackUrl().contains("__EVENT__")
					|| !monitor.getDownstreamCallbackUrl().contains("__EVENT_ID__")) {
				throw new IllegalArgumentException("模板 GET 下游回调地址必须包含 __EVENT__ 和 __EVENT_ID__ 宏");
			}
			validateCallbackMacros(monitor.getDownstreamCallbackUrl());
		}
		if (!"OFF".equals(monitor.getRtaMode())) {
			throw new IllegalArgumentException("首期 RTA 只能为 OFF");
		}
		if (!("OFF".equals(monitor.getBillingMode()) || "AUDIT_ONLY".equals(monitor.getBillingMode())
				|| "CPC".equals(monitor.getBillingMode()))) {
			throw new IllegalArgumentException("计费模式只能为 OFF、AUDIT_ONLY 或 CPC");
		}
		if (!"OFF".equals(monitor.getBillingMode())
				&& (monitor.getUnitPrice() == null || monitor.getUnitPrice().signum() <= 0)) {
			throw new IllegalArgumentException("启用点击审计或 CPC 计费时单价必须大于 0");
		}
	}

	private void validateCallbackMacros(String callbackUrl) {
		Matcher matcher = TEMPLATE_MACRO.matcher(callbackUrl);
		while (matcher.find()) {
			if (!TEMPLATE_GET_DOWNSTREAM_MACROS.contains(matcher.group())) {
				throw new IllegalArgumentException("模板 GET 下游回调地址包含不支持的宏：" + matcher.group());
			}
		}
	}

	private void validateMacros(AdxAdvertiser advertiser, Set<String> supportedMacros, String adapterName) {
		Matcher matcher = TEMPLATE_MACRO.matcher(advertiser.getBidUrl());
		while (matcher.find()) {
			if (!supportedMacros.contains(matcher.group())) {
				throw new IllegalArgumentException(adapterName + " 请求地址包含不支持的宏：" + matcher.group());
			}
		}
		if (!StringUtils.hasText(advertiser.getMacroParams())) {
			return;
		}
		for (String required : advertiser.getMacroParams().split("[,\\s]+")) {
			if (!supportedMacros.contains(required)) {
				throw new IllegalArgumentException(adapterName + " 必填宏配置无效：" + required);
			}
			if (!advertiser.getBidUrl().contains(required)) {
				throw new IllegalArgumentException(adapterName + " 请求地址缺少已声明的必填宏：" + required);
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
