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

	private static final Pattern COCO_MACRO = Pattern.compile("__[A-Z0-9_]+__");

	private static final Set<String> COCO_MACROS = Set.of("__IDFA__", "__IDFA_MD5__", "__IMEI__", "__IMEI_MD5__",
			"__OAID__", "__OAID_MD5__", "__IP__", "__CAID_LIST__", "__ANDROID_ID__", "__ANDROID_ID_MD5__", "__AAID__",
			"__UA__", "__OS__", "__MODEL__", "__CALLBACK__", "__CLICKID__", "__CREATIVEID__");

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
		validateHttps(advertiser.getBidUrl(), advertiser.getUpstreamHostAllowlist(), "Coco 请求地址");
		if ("COCO".equals(advertiser.getAdapterCode())) {
			if (!advertiser.getBidUrl().contains("__CALLBACK__")) {
				throw new IllegalArgumentException("Coco 请求地址必须包含 __CALLBACK__ 宏");
			}
			validateCocoMacros(advertiser);
		}
		if (!StringUtils.hasText(monitor.getDownstreamCallbackUrl())) {
			throw new IllegalArgumentException("发布路由必须配置下游回调地址");
		}
		validateHttps(monitor.getDownstreamCallbackUrl(), channel.getCallbackHostAllowlist(), "下游回调地址");
		if (!"OFF".equals(monitor.getRtaMode())) {
			throw new IllegalArgumentException("首期 RTA 只能为 OFF");
		}
		if (!("OFF".equals(monitor.getBillingMode()) || "AUDIT_ONLY".equals(monitor.getBillingMode()))) {
			throw new IllegalArgumentException("首期计费只能为 OFF 或 AUDIT_ONLY");
		}
	}

	private void validateCocoMacros(AdxAdvertiser advertiser) {
		Matcher matcher = COCO_MACRO.matcher(advertiser.getBidUrl());
		while (matcher.find()) {
			if (!COCO_MACROS.contains(matcher.group())) {
				throw new IllegalArgumentException("Coco 请求地址包含不支持的宏：" + matcher.group());
			}
		}
		if (!StringUtils.hasText(advertiser.getMacroParams())) {
			return;
		}
		for (String required : advertiser.getMacroParams().split("[,\\s]+")) {
			if (!COCO_MACROS.contains(required)) {
				throw new IllegalArgumentException("Coco 必填宏配置无效：" + required);
			}
			if (!advertiser.getBidUrl().contains(required)) {
				throw new IllegalArgumentException("Coco 请求地址缺少已声明的必填宏：" + required);
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
