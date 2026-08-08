package com.yzytech.scx.ocpx.runtime.adapter;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTPS GET 宏模板的公共渲染器。
 *
 * <p>
 * 这个类只负责“把标准字段填入 URL”这一件事。模板地址的 HTTPS、域名白名单等安全校验由路由发布服务负责； 合作方私有签名、加密、POST
 * 请求或业务响应码判断则必须由专用 Adapter 负责。
 * </p>
 *
 * <p>
 * 宏值按 query parameter value 编码一次。调用方应传入原始值，不能提前编码，否则中文、UA、callback 等字段会发生二次编码。
 * </p>
 */
@Component
public class GetMacroTemplateRenderer {

	private static final Pattern MACRO_PATTERN = Pattern.compile("__[A-Z0-9_]+__");

	/** 防止异常配置生成过长请求行，也给常见网关和上游服务留出安全余量。 */
	private static final int MAX_URL_LENGTH = 16_384;

	/**
	 * 渲染一个 GET URL 模板。
	 * @param template 路由发布时固化的 URL 模板
	 * @param values 宏与原始值的映射；可选字段为空时替换为空字符串
	 * @param supportedMacros 当前 Adapter 声明支持的宏白名单
	 * @param requiredMacros 当前路由要求必须有值的宏
	 * @param protocolName 用于生成便于排查的错误信息，例如 TEMPLATE_GET
	 * @return 已完成单次 query 编码的最终 URL
	 */
	public String render(String template, Map<String, String> values, Set<String> supportedMacros,
			Set<String> requiredMacros, String protocolName) {
		if (template == null || template.isBlank()) {
			throw new IllegalArgumentException(protocolName + " URL is not configured");
		}
		Matcher matcher = MACRO_PATTERN.matcher(template);
		while (matcher.find()) {
			String macro = matcher.group();
			if (!supportedMacros.contains(macro)) {
				throw new IllegalArgumentException(protocolName + " URL contains unsupported macro: " + macro);
			}
		}
		for (String required : requiredMacros) {
			if (!supportedMacros.contains(required)) {
				throw new IllegalArgumentException(protocolName + " required macro is unsupported: " + required);
			}
			if (!template.contains(required)) {
				throw new IllegalArgumentException(protocolName + " URL is missing required macro: " + required);
			}
			String value = values.get(required);
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException(protocolName + " required macro has no value: " + required);
			}
		}
		String rendered = template;
		for (String macro : supportedMacros) {
			String value = values.get(macro);
			rendered = rendered.replace(macro, encode(value));
		}
		if (MACRO_PATTERN.matcher(rendered).find()) {
			throw new IllegalArgumentException(protocolName + " URL contains unresolved macros");
		}
		if (rendered.length() > MAX_URL_LENGTH) {
			throw new IllegalArgumentException(protocolName + " URL exceeds safe length limit");
		}
		return rendered;
	}

	private String encode(String value) {
		return value == null ? "" : UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
	}

}
