package com.yzytech.scx.ocpx.rta.contract;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Build-time protocol definitions shared by Admin and Engine; never contains secrets. */
public final class RtaProtocols {

	public record Field(String key, String label, String pattern) {
	}

	public record Definition(String code, String name, String mode, int maxCandidates, boolean retrySafe,
			boolean credentialRequired, String fixedEndpoint, List<Field> configFields, List<Field> bindingFields,
			List<String> routeIdentityFields) {
		public void validateRoute(String link, Map<String, String> config) {
			if (!routeIdentityFields.isEmpty())
				requireRoute(link, config, routeIdentityFields);
		}

		public Map<String, String> validateConfig(Map<String, String> input) {
			var values = input == null ? Map.<String, String>of() : input;
			if (values.size() != configFields.size())
				throw new IllegalArgumentException("协议配置字段不匹配");
			for (var field : configFields) {
				String value = values.get(field.key());
				if (value == null || !value.matches(field.pattern()))
					throw new IllegalArgumentException("协议配置无效：" + field.label());
			}
			return Map.copyOf(values);
		}
	}

	public static final List<Definition> ALL = List.of(
			new Definition("GENERIC_ASYNC", "通用异步", "ASYNC_CALLBACK", 100, true, true, "", List.of(),
					List.of(new Field("productId", "上游产品 ID", ".{1,128}"),
							new Field("externalTaskId", "上游任务 ID", ".{1,128}")),
					List.of()),
			new Definition("WEIFENG_RTA", "微风 RTA", "SYNC_HTTP", 1, false, false,
					"https://rta.adapi.cn/rtaCommon/sendRta", List.of(new Field("aid", "微风广告计划 ID", "[0-9]{1,32}"),
							new Field("did", "微风媒体标识", "[A-Za-z0-9._-]{1,64}")),
					List.of(), List.of("aid", "did")));

	private RtaProtocols() {
	}

	public static Definition require(String code) {
		return ALL.stream()
			.filter(p -> p.code().equals(code))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("不支持的 RTA 协议"));
	}

	/** Literal query values only; duplicate identity parameters are never accepted. */
	public static void requireWeifengRoute(String link, Map<String, String> config) {
		requireRoute(link, config, List.of("aid", "did"));
	}

	private static void requireRoute(String link, Map<String, String> config, List<String> fields) {
		if (link == null || !link.contains("?"))
			throw new IllegalArgumentException("点击链接缺少微风计划参数");
		var found = new HashMap<String, String>();
		for (String pair : link.substring(link.indexOf('?') + 1).split("&")) {
			String[] parts = pair.split("=", 2);
			String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
			if (!fields.contains(key))
				continue;
			String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
			if (found.putIfAbsent(key, value) != null)
				throw new IllegalArgumentException("点击链接存在重复计划参数");
		}
		if (fields.stream().anyMatch(field -> !Objects.equals(found.get(field), config.get(field))))
			throw new IllegalArgumentException("RTA 与点击链接的 aid/did 不一致");
	}

}
