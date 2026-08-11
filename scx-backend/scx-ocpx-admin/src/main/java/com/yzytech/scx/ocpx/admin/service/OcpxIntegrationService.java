package com.yzytech.scx.ocpx.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.dto.CatalogViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorLinkMapper;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.PermissionLevel;
import com.yzytech.scx.ocpx.admin.service.OcpxAccessScope.ScopeType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 生成一个监测链接的 OCPX 接入说明。
 *
 * <p>
 * 返回值只包含 URL、协议版本、签名公式和占位示例，不返回渠道密钥、设备标识或 callback token。 下游可以直接复制到 Apifox；上游 callback 中的
 * ext 仍由 SCX 每次发送点击时动态生成。
 * </p>
 */
@Service
public class OcpxIntegrationService {

	private static final String SIGNATURE_FORMULA = "HMAC-SHA256(secret, METHOD + \\n + PATH + \\n + X-Timestamp + \\n + X-Nonce + \\n + SHA256(rawBody))";

	private final OcpxMonitorLinkMapper monitorMapper;

	private final OcpxChannelMapper channelMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxAccessScope accessScope;

	private final ObjectMapper objectMapper;

	public OcpxIntegrationService(OcpxMonitorLinkMapper monitorMapper, OcpxChannelMapper channelMapper,
			OcpxAdvertiserMapper advertiserMapper, OcpxAccessScope accessScope, ObjectMapper objectMapper) {
		this.monitorMapper = monitorMapper;
		this.channelMapper = channelMapper;
		this.advertiserMapper = advertiserMapper;
		this.accessScope = accessScope;
		this.objectMapper = objectMapper;
	}

	public CatalogViews.OcpxIntegration get(String monitorId) {
		OcpxMonitorLink monitor = require(monitorMapper.selectById(monitorId), "监测链接不存在");
		accessScope.require(ScopeType.ADVERTISER, monitor.getAdvertiserId(), PermissionLevel.VIEW);
		accessScope.require(ScopeType.CHANNEL, monitor.getChannelId(), PermissionLevel.VIEW);
		OcpxChannel channel = require(channelMapper.selectById(monitor.getChannelId()), "渠道不存在");
		OcpxAdvertiser advertiser = require(advertiserMapper.selectById(monitor.getAdvertiserId()), "广告主不存在");
		String feedbackUrl = firstNonBlank(monitor.getClickUrl(), monitor.getShowUrl());
		if (!StringUtils.hasText(feedbackUrl)) {
			throw new IllegalArgumentException("监测链接尚未生成 Feedback URL，请先保存监测链接");
		}
		List<String> headers = "HMAC".equalsIgnoreCase(channel.getAuthType())
				? List.of("Content-Type: application/json", "X-Client-Id: " + channel.getChannelCode(),
						"X-Key-Version: " + channel.getKeyVersion(), "X-Timestamp: <Unix毫秒>", "X-Nonce: <每次请求唯一随机串>",
						"X-Sign: <HMAC-SHA256结果>")
				: List.of("Content-Type: application/json");
		return new CatalogViews.OcpxIntegration(monitor.getId(), monitor.getMonitorName(), monitor.getRouteStatus(),
				"1.0", "POST", feedbackUrl, channel.getAuthType(), channel.getKeyVersion(),
				StringUtils.hasText(channel.getSecretKey()), headers,
				"HMAC".equalsIgnoreCase(channel.getAuthType()) ? SIGNATURE_FORMULA : "当前渠道未启用签名",
				requestExample(monitor),
				"/open-api/ocpx/v1/upstreams/" + advertiser.getAdapterCode().toLowerCase(Locale.ROOT)
						+ "/callback?ext=<SCX动态令牌>",
				advertiser.getAdapterCode(), channel.getAdapterCode(),
				"docs/广告平台设计/contracts/scx-ocpx-v1.postman_collection.json");
	}

	private String requestExample(OcpxMonitorLink monitor) {
		try {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("schema_version", "1.0");
			body.put("event_id", "click-demo-001");
			body.put("event", "click");
			body.put("event_time", 1_785_744_000_000L);
			body.put("monitor_id", monitor.getId());
			body.put("clickid", "click-demo-001");
			body.put("os", "android");
			body.put("oaid_md5", "<32位MD5占位值>");
			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
		}
		catch (Exception error) {
			throw new IllegalStateException("无法生成 OCPX 请求示例", error);
		}
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return null;
	}

	private <T> T require(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

}
