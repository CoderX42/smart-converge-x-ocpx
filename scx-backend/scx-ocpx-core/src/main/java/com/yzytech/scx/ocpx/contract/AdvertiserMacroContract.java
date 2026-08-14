package com.yzytech.scx.ocpx.contract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 广告主上游请求的宏查询参数拼接契约。 */
public final class AdvertiserMacroContract {

	private AdvertiserMacroContract() {
	}

	/**
	 * 将宏参数拼到请求地址，自动选择 {@code ?}/{@code &}，并跳过地址中已存在的同名参数。
	 *
	 * <p>
	 * 历史数据可能将 {@code macroParams} 保存为逗号分隔的宏名清单；此类非查询串值不会被追加。
	 * @param bidUrl 广告主基础请求地址
	 * @param macroParams {@code key=value&key=value} 形式的宏参数
	 * @return 有效上游请求模板
	 */
	public static String appendToBidUrl(String bidUrl, String macroParams) {
		if (bidUrl == null || macroParams == null) {
			return bidUrl;
		}
		String query = trimDelimiters(macroParams.trim());
		if (query.isEmpty() || !query.contains("=")) {
			return bidUrl;
		}

		int fragmentIndex = bidUrl.indexOf('#');
		String base = fragmentIndex < 0 ? bidUrl : bidUrl.substring(0, fragmentIndex);
		String fragment = fragmentIndex < 0 ? "" : bidUrl.substring(fragmentIndex);
		Set<String> existingKeys = queryKeys(base);
		List<String> missingPairs = new ArrayList<>();
		for (String candidate : query.split("&")) {
			String pair = candidate.trim();
			String key = queryKey(pair);
			if (!pair.isEmpty() && !key.isEmpty() && existingKeys.add(key)) {
				missingPairs.add(pair);
			}
		}
		if (missingPairs.isEmpty()) {
			return bidUrl;
		}

		String separator = base.endsWith("?") || base.endsWith("&") ? "" : base.contains("?") ? "&" : "?";
		return base + separator + String.join("&", missingPairs) + fragment;
	}

	private static Set<String> queryKeys(String url) {
		Set<String> keys = new HashSet<>();
		int queryIndex = url.indexOf('?');
		if (queryIndex < 0 || queryIndex == url.length() - 1) {
			return keys;
		}
		for (String pair : url.substring(queryIndex + 1).split("&")) {
			String key = queryKey(pair.trim());
			if (!key.isEmpty()) {
				keys.add(key);
			}
		}
		return keys;
	}

	private static String queryKey(String pair) {
		int equalsIndex = pair.indexOf('=');
		return (equalsIndex < 0 ? pair : pair.substring(0, equalsIndex)).trim();
	}

	private static String trimDelimiters(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && (value.charAt(start) == '?' || value.charAt(start) == '&')) {
			start++;
		}
		while (end > start && value.charAt(end - 1) == '&') {
			end--;
		}
		return value.substring(start, end);
	}

}
