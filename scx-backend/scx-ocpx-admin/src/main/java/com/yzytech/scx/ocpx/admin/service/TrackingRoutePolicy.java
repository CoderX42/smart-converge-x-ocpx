package com.yzytech.scx.ocpx.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TrackingRoutePolicy {

	private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");

	private static final Pattern MACRO = Pattern.compile("^__[A-Z0-9_]{2,64}__$");

	private static final int MAX_TEMPLATE_LENGTH = 2048;

	private final OcpxTrackingProperties properties;

	private final ObjectMapper objectMapper;

	private final SecureRandom random = new SecureRandom();

	public TrackingRoutePolicy(OcpxTrackingProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public Assessment assess(OcpxAdapterService.PublishedAdapter adapter) {
		List<Check> checks = new ArrayList<>();
		URI base = configuredBase(checks);
		if (adapter.trackingMacroSchemaVersion() != 1 || adapter.trackingMacroPairs().isEmpty()) {
			checks.add(new Check("MACRO_CAPABILITY", "FAIL", "Channel Adapter 未声明 S7 监测宏能力"));
		}
		else {
			checks.add(new Check("MACRO_CAPABILITY", "PASS", "Adapter 监测宏 schema 已冻结"));
		}
		validatePairs(adapter.trackingMacroPairs(), checks);
		String schemaJson = macroSchemaJson(adapter);
		String templateFingerprint = sha256((base == null ? "UNCONFIGURED" : base.getScheme() + "://" + base.getHost())
				+ "|" + schemaJson + "|/ocpx/c/{routeCode}");
		return new Assessment(checks.stream().anyMatch(item -> "FAIL".equals(item.status())) ? "FAIL" : "PASS", checks,
				schemaJson, templateFingerprint, base != null);
	}

	public String newRouteCode() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String buildTemplate(String routeCode, OcpxAdapterService.PublishedAdapter adapter) {
		Assessment assessment = assess(adapter);
		if (!assessment.publishable()) {
			throw new IllegalStateException("Tracking route configuration is not publishable");
		}
		URI base = URI.create(properties.getPublicBaseUrl().trim());
		StringBuilder template = new StringBuilder(base.toString().replaceAll("/+$", "")).append("/ocpx/c/")
			.append(routeCode);
		for (int index = 0; index < adapter.trackingMacroPairs().size(); index++) {
			OcpxAdapterService.TrackingMacroPair pair = adapter.trackingMacroPairs().get(index);
			template.append(index == 0 ? '?' : '&').append(pair.key()).append('=').append(pair.macro());
		}
		validateTemplate(template.toString(), adapter.trackingMacroPairs());
		return template.toString();
	}

	public void validateTemplate(String template, List<OcpxAdapterService.TrackingMacroPair> allowedPairs) {
		if (!StringUtils.hasText(template) || template.length() > MAX_TEMPLATE_LENGTH) {
			throw new IllegalArgumentException("监测地址模板为空或超过 2048 字符");
		}
		URI uri = URI.create(template);
		if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
				|| uri.getUserInfo() != null) {
			throw new IllegalArgumentException("监测地址必须为无 userinfo 的 HTTPS 地址");
		}
		Set<String> allowed = new HashSet<>();
		allowedPairs.forEach(pair -> allowed.add(pair.key() + "=" + pair.macro()));
		Set<String> seen = new HashSet<>();
		String query = uri.getRawQuery();
		if (query == null) {
			throw new IllegalArgumentException("监测地址缺少 Adapter 宏查询参数");
		}
		for (String item : query.split("&", -1)) {
			int separator = item.indexOf('=');
			if (separator < 1 || item.indexOf('=', separator + 1) >= 0) {
				throw new IllegalArgumentException("监测宏查询参数编码无效");
			}
			String key = item.substring(0, separator);
			String value = item.substring(separator + 1);
			if (!seen.add(key)) {
				throw new IllegalArgumentException("监测宏查询参数存在重复键");
			}
			if (!KEY.matcher(key).matches() || !MACRO.matcher(value).matches() || !allowed.contains(item)) {
				throw new IllegalArgumentException("监测地址包含未知键或未知宏");
			}
			if ("callback".equals(key) && !"__CALLBACK__".equals(value)) {
				throw new IllegalArgumentException("callback 字段只能使用受控占位宏");
			}
		}
		for (OcpxAdapterService.TrackingMacroPair pair : allowedPairs) {
			if (pair.required() && !seen.contains(pair.key())) {
				throw new IllegalArgumentException("监测地址缺少 Adapter 必需宏");
			}
		}
	}

	private URI configuredBase(List<Check> checks) {
		try {
			URI uri = URI.create(properties.getPublicBaseUrl().trim());
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank() || uri.getUserInfo() != null
					|| host.endsWith(".invalid") || "invalid".equals(host) || isUnsafeLiteralHost(host)) {
				checks.add(new Check("TRACKING_HOST", "FAIL", "监测入口未配置为可验收的 HTTPS 主机"));
				return null;
			}
			checks.add(new Check("TRACKING_HOST", "PASS", "监测入口已配置；检查响应不回显主机或完整地址"));
			return uri;
		}
		catch (RuntimeException error) {
			checks.add(new Check("TRACKING_HOST", "FAIL", "监测入口配置格式无效"));
			return null;
		}
	}

	private boolean isUnsafeLiteralHost(String host) {
		return "localhost".equals(host) || host.startsWith("127.") || host.startsWith("10.")
				|| host.startsWith("192.168.") || host.startsWith("169.254.") || host.equals("::1")
				|| host.startsWith("172.16.") || host.startsWith("172.17.") || host.startsWith("172.18.")
				|| host.startsWith("172.19.") || host.startsWith("172.2") || host.startsWith("172.30.")
				|| host.startsWith("172.31.");
	}

	private void validatePairs(List<OcpxAdapterService.TrackingMacroPair> pairs, List<Check> checks) {
		Set<String> keys = new HashSet<>();
		Set<String> macros = new HashSet<>();
		for (OcpxAdapterService.TrackingMacroPair pair : pairs) {
			if (!KEY.matcher(pair.key()).matches() || !MACRO.matcher(pair.macro()).matches() || !keys.add(pair.key())
					|| !macros.add(pair.macro())
					|| ("callback".equals(pair.key()) && !"__CALLBACK__".equals(pair.macro()))) {
				checks.add(new Check("MACRO_SCHEMA", "FAIL", "Adapter 宏键、占位符、重复项或 callback 约束无效"));
				return;
			}
		}
		if (pairs.stream().noneMatch(OcpxAdapterService.TrackingMacroPair::required)) {
			checks.add(new Check("MACRO_SCHEMA", "FAIL", "Adapter 必须声明至少一个必需监测宏"));
			return;
		}
		checks.add(new Check("MACRO_SCHEMA", "PASS", "宏白名单、重复键、必需项与 callback 约束通过"));
	}

	private String macroSchemaJson(OcpxAdapterService.PublishedAdapter adapter) {
		try {
			return objectMapper.writeValueAsString(new MacroSchema(adapter.trackingMacroSchemaVersion(),
					adapter.adapterCode(), adapter.trackingMacroPairs()));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法冻结 Adapter 宏 schema", error);
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算监测地址模板摘要", error);
		}
	}

	public record Assessment(String status, List<Check> checks, String macroSchemaJson, String templateFingerprint,
			boolean hostConfigured) {

		public boolean publishable() {
			return "PASS".equals(status);
		}
	}

	public record Check(String code, String status, String message) {
	}

	private record MacroSchema(int schemaVersion, String adapterCode, List<OcpxAdapterService.TrackingMacroPair> pairs) {
	}

}
