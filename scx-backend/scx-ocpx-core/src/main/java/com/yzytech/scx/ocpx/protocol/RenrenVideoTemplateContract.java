package com.yzytech.scx.ocpx.protocol;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 人人视频 CPA 点击模板的管理面与数据面共享契约。 */
public final class RenrenVideoTemplateContract {

	public static final int ANDROID_PLATFORM = 1;

	public static final int IOS_PLATFORM = 2;

	private static final int MAX_URL_LENGTH = 5000;

	private static final Pattern MACRO = Pattern.compile("__[A-Z0-9_]+__", Pattern.CASE_INSENSITIVE);

	private static final Set<String> SUPPORTED_MACROS = Set.of("__CAID__", "__IDFA__", "__IDFA_MD5__", "__TS__",
			"__TS_S__", "__IP__", "__OS__", "__UA__", "__MODEL__", "__IMEI__", "__OAID__", "__OAID_MD5__",
			"__ANDROID_ID__", "__ANDROID_ID_MD5__", "__REQID__", "__CLICK_ID__", "__PASS_THROUGH__", "__CALLBACK__");

	private static final Map<String, String> COMMON_REQUIRED = Map.of("ip", "__IP__", "os", "__OS__", "ua", "__UA__",
			"callback", "__CALLBACK__", "requestid", "__REQID__", "clickid", "__CLICK_ID__");

	private static final Map<String, String> ANDROID_REQUIRED = Map.of("imeimd5", "__IMEI__", "oaid", "__OAID__",
			"oaidmd5", "__OAID_MD5__");

	private static final Set<String> ANDROID_ONLY_MACROS = Set.of("__IMEI__", "__OAID__", "__OAID_MD5__",
			"__ANDROID_ID__", "__ANDROID_ID_MD5__");

	private static final Map<String, String> IOS_IDENTITIES = Map.of("caid", "__CAID__", "idfa", "__IDFA__", "idfamd5",
			"__IDFA_MD5__");

	private static final Set<String> IOS_ONLY_MACROS = Set.copyOf(IOS_IDENTITIES.values());

	private RenrenVideoTemplateContract() {
	}

	/** 校验并解析完整点击模板；动态宏只允许作为完整查询参数值出现。 */
	public static Template requireValid(String value, Integer platform) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
			throw invalid("RENREN_VIDEO_TEMPLATE_URL_INVALID");
		}
		if (value.contains("{") || value.contains("}")) {
			throw invalid("RENREN_VIDEO_TEMPLATE_MACRO_INVALID");
		}
		if (!Integer.valueOf(ANDROID_PLATFORM).equals(platform) && !Integer.valueOf(IOS_PLATFORM).equals(platform)) {
			throw invalid("RENREN_VIDEO_TEMPLATE_PLATFORM_INVALID");
		}
		try {
			URI uri = URI.create(value.trim()).normalize();
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					|| !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null || uri.getFragment() != null
					|| uri.getPort() == 0 || uri.getPort() > 65535 || !"/ad-action/click".equals(uri.getPath())) {
				throw invalid("RENREN_VIDEO_TEMPLATE_URL_INVALID");
			}
			if (!StringUtils.hasText(uri.getRawQuery())) {
				throw invalid("RENREN_VIDEO_TEMPLATE_QUERY_MISSING");
			}

			List<QueryParameter> parameters = new ArrayList<>();
			Set<String> names = new HashSet<>();
			for (String segment : uri.getRawQuery().split("&", -1)) {
				int separator = segment.indexOf('=');
				if (separator <= 0) {
					throw invalid("RENREN_VIDEO_TEMPLATE_PARAMETER_INVALID");
				}
				String name = decode(segment.substring(0, separator)).trim();
				String parameterValue = decode(segment.substring(separator + 1));
				String normalizedName = name.toLowerCase(Locale.ROOT);
				if (!StringUtils.hasText(name) || !names.add(normalizedName)) {
					throw invalid("RENREN_VIDEO_TEMPLATE_PARAMETER_DUPLICATE");
				}
				if (parameterValue.contains("{") || parameterValue.contains("}")) {
					throw invalid("RENREN_VIDEO_TEMPLATE_MACRO_INVALID");
				}
				var matcher = MACRO.matcher(parameterValue);
				if (matcher.find() && (!matcher.group().equals(parameterValue)
						|| !SUPPORTED_MACROS.contains(parameterValue) || matcher.find())) {
					throw invalid("RENREN_VIDEO_TEMPLATE_MACRO_INVALID");
				}
				parameters.add(new QueryParameter(name, parameterValue));
			}

			Map<String, String> parameterMap = parameters.stream()
				.collect(java.util.stream.Collectors.toMap(item -> item.name().toLowerCase(Locale.ROOT),
						QueryParameter::value));
			requireFixed(parameterMap, "userid");
			COMMON_REQUIRED.forEach((name, macro) -> requireMacro(parameterMap, name, macro));
			String timestamp = parameterMap.get("ts");
			if (!("__TS__".equals(timestamp) || "__TS_S__".equals(timestamp))) {
				throw invalid("RENREN_VIDEO_TEMPLATE_TIMESTAMP_MISSING");
			}
			if (Integer.valueOf(ANDROID_PLATFORM).equals(platform)) {
				rejectOwnedMacros(parameters, IOS_ONLY_MACROS);
				ANDROID_REQUIRED.forEach((name, macro) -> requireMacro(parameterMap, name, macro));
			}
			else {
				rejectOwnedMacros(parameters, ANDROID_ONLY_MACROS);
				if (IOS_IDENTITIES.entrySet()
					.stream()
					.noneMatch(entry -> entry.getValue().equals(parameterMap.get(entry.getKey())))) {
					throw invalid("RENREN_VIDEO_TEMPLATE_IOS_IDENTITY_MISSING");
				}
			}
			return new Template(uri, List.copyOf(parameters));
		}
		catch (IllegalArgumentException exception) {
			if (exception.getMessage() != null && exception.getMessage().startsWith("RENREN_VIDEO_")) {
				throw exception;
			}
			throw invalid("RENREN_VIDEO_TEMPLATE_URL_INVALID");
		}
	}

	private static void requireFixed(Map<String, String> parameters, String name) {
		String value = parameters.get(name);
		if (!StringUtils.hasText(value) || MACRO.matcher(value).find()) {
			throw invalid("RENREN_VIDEO_TEMPLATE_USER_ID_MISSING");
		}
	}

	private static void requireMacro(Map<String, String> parameters, String name, String macro) {
		if (!macro.equals(parameters.get(name))) {
			throw invalid("RENREN_VIDEO_TEMPLATE_REQUIRED_MACRO_MISSING");
		}
	}

	private static void rejectOwnedMacros(List<QueryParameter> parameters, Set<String> forbidden) {
		if (parameters.stream().map(QueryParameter::value).anyMatch(forbidden::contains)) {
			throw invalid("RENREN_VIDEO_TEMPLATE_PLATFORM_MACRO_MISMATCH");
		}
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private static IllegalArgumentException invalid(String code) {
		return new IllegalArgumentException(code);
	}

	public record Template(URI uri, List<QueryParameter> parameters) {
	}

	public record QueryParameter(String name, String value) {
	}

}
