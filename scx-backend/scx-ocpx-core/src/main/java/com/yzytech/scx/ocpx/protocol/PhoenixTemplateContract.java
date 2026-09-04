package com.yzytech.scx.ocpx.protocol;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 凤凰新闻 CPA 模板的管理面与数据面共享契约。
 *
 * <p>凤凰协议通常使用 {@code /d/imp} 曝光和 {@code /d/cli} 点击。管理端保留合作方提交的
 * HTTPS 点击路径，SCX 不再强制路径必须等于其中某一个固定值；曝光仍使用 {@code /d/imp}。
 * cid/gid/aid/mid/channel 是凤凰在实际投放中分配的
 * 固定参数，SCX 只校验它们存在且不是动态宏，不把它们臆造为默认值。</p>
 */
public final class PhoenixTemplateContract {

	public static final int ANDROID_PLATFORM = 1;

	public static final int IOS_PLATFORM = 2;

	public static final int ANDROID_IOS_PLATFORM = 3;

	public static final String HOST = "api.feather.ifeng.com";

	public static final String CLICK_PATH = "/d/cli";

	public static final String IMPRESSION_PATH = "/d/imp";

	/** 保留旧调用方的别名；凤凰出站点击路径为 {@link #CLICK_PATH}。 */
	public static final String PATH = CLICK_PATH;

	private static final int MAX_URL_LENGTH = 5000;

	private static final Pattern MACRO = Pattern.compile("__[A-Z0-9_]+__", Pattern.CASE_INSENSITIVE);

	private static final Set<String> FIXED_PARAMETER_NAMES = Set.of("cid", "gid", "aid", "mid", "channel");

	private static final Set<String> ALLOWED_PARAMETERS = Set.of("cid", "gid", "aid", "mid", "channel", "reqid",
			"tagid", "os", "ip", "ua", "model", "ts", "idfa", "idfa_md5", "caid", "imei", "oaid",
			"oaid_md5", "android_id", "callback");

	private static final Set<String> SUPPORTED_MACROS = Set.of("__REQID__", "__TAGID__", "__OS__", "__IP__",
			"__UA__", "__MODEL__", "__TS__", "__IDFA__", "__IDFAMD5__", "__CAID__", "__IMEI__", "__OAID__",
			"__OAIDMD5__", "__ANDROID__", "__CALLBACK__");

	private static final Set<String> COMMON_REQUIRED_MACROS = Set.of("reqid", "os", "ts");

	private PhoenixTemplateContract() {
	}

	/** 校验凤凰点击模板；平台 1=Android、2=iOS、3=Android+iOS。 */
	public static Template requireValid(String value, Integer platform) {
		return requireValid(value, platform, true);
	}

	/**
	 * 校验凤凰模板。
	 * @param clickTemplate 是否为点击模板；点击模板要求 callback 宏，曝光投递会省略 callback
	 */
	public static Template requireValid(String value, Integer platform, boolean clickTemplate) {
		if (!StringUtils.hasText(value) || value.length() > MAX_URL_LENGTH
				|| !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
			throw invalid("PHOENIX_TEMPLATE_URL_INVALID");
		}
		if (!isSupportedPlatform(platform)) {
			throw invalid("PHOENIX_TEMPLATE_PLATFORM_INVALID");
		}
		try {
			URI uri = URI.create(value.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())
					|| uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null
					|| !StringUtils.hasText(uri.getRawQuery())) {
				throw invalid("PHOENIX_TEMPLATE_URL_INVALID");
			}

			List<QueryParameter> parameters = new ArrayList<>();
			Map<String, String> values = new LinkedHashMap<>();
			Set<String> names = new HashSet<>();
			for (String segment : uri.getRawQuery().split("&", -1)) {
				int separator = segment.indexOf('=');
				if (separator <= 0) {
					throw invalid("PHOENIX_TEMPLATE_PARAMETER_INVALID");
				}
				String name = decode(segment.substring(0, separator));
				String parameterValue = decode(segment.substring(separator + 1));
				String normalizedName = name.toLowerCase(Locale.ROOT);
				if (!StringUtils.hasText(name) || !name.equals(normalizedName)
						|| !ALLOWED_PARAMETERS.contains(normalizedName)) {
					throw invalid("PHOENIX_TEMPLATE_UNKNOWN_PARAMETER");
				}
				if (!names.add(normalizedName)) {
					throw invalid("PHOENIX_TEMPLATE_PARAMETER_DUPLICATE");
				}
				if (!StringUtils.hasText(parameterValue) || parameterValue.contains("{") || parameterValue.contains("}")
						|| parameterValue.chars().anyMatch(Character::isISOControl)) {
					throw invalid("PHOENIX_TEMPLATE_PARAMETER_INVALID");
				}
				var matcher = MACRO.matcher(parameterValue);
				if (matcher.find()) {
					if (!matcher.group().equals(parameterValue) || !SUPPORTED_MACROS.contains(parameterValue)
							|| matcher.find()) {
						throw invalid("PHOENIX_TEMPLATE_MACRO_INVALID");
					}
				}
				else if (parameterValue.contains("__")) {
					throw invalid("PHOENIX_TEMPLATE_MACRO_INVALID");
				}
				if (parameterValue.matches("(?i).*([a-z][a-z0-9+.-]*:)?//.*")
						|| parameterValue.matches("(?i)^(?:javascript|data|file):.*")) {
					throw invalid("PHOENIX_TEMPLATE_REDIRECT_FORBIDDEN");
				}
				parameters.add(new QueryParameter(name, parameterValue));
				values.put(normalizedName, parameterValue);
			}

			for (String fixedName : FIXED_PARAMETER_NAMES) {
				String fixedValue = values.get(fixedName);
				if (!StringUtils.hasText(fixedValue) || MACRO.matcher(fixedValue).matches()) {
					throw invalid("PHOENIX_TEMPLATE_FIXED_PARAMETER_INVALID");
				}
			}
			for (String required : COMMON_REQUIRED_MACROS) {
				if (!isMacro(values.get(required), macroFor(required))) {
					throw invalid("PHOENIX_TEMPLATE_REQUIRED_MACRO_MISSING");
				}
			}
			if (clickTemplate && !isMacro(values.get("callback"), "__CALLBACK__")) {
				throw invalid("PHOENIX_TEMPLATE_REQUIRED_MACRO_MISSING");
			}
			if (platform == ANDROID_PLATFORM || platform == ANDROID_IOS_PLATFORM) {
				if (!isMacro(values.get("oaid"), "__OAID__") || !isMacro(values.get("oaid_md5"), "__OAIDMD5__")) {
					throw invalid("PHOENIX_TEMPLATE_DEVICE_MACRO_MISSING");
				}
			}
			if (platform == IOS_PLATFORM || platform == ANDROID_IOS_PLATFORM) {
				if (!isMacro(values.get("caid"), "__CAID__")) {
					throw invalid("PHOENIX_TEMPLATE_DEVICE_MACRO_MISSING");
				}
			}
			return new Template(uri, List.copyOf(parameters));
		}
		catch (IllegalArgumentException exception) {
			if (exception.getMessage() != null && exception.getMessage().startsWith("PHOENIX_")) {
				throw exception;
			}
			throw invalid("PHOENIX_TEMPLATE_URL_INVALID");
		}
	}

	private static boolean isSupportedPlatform(Integer platform) {
		return Integer.valueOf(ANDROID_PLATFORM).equals(platform) || Integer.valueOf(IOS_PLATFORM).equals(platform)
				|| Integer.valueOf(ANDROID_IOS_PLATFORM).equals(platform);
	}

	private static String macroFor(String name) {
		return switch (name) {
			case "reqid" -> "__REQID__";
			case "os" -> "__OS__";
			case "ts" -> "__TS__";
			default -> "";
		};
	}

	private static boolean isMacro(String value, String expected) {
		return expected.equals(value);
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
