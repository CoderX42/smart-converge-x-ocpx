package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;

/** 三种上游协议共享的宏值、归因标识和模板残留审计。 */
@Component
public class MacroResolutionService {

	private static final Pattern MACRO = Pattern.compile("__[A-Z0-9_]+__");

	private static final Set<String> IOS_IDS = Set.of("__IDFA__", "__IDFA_MD5__", "__CAID__", "__CAID_LIST__",
			"__CAID1__", "__CAID2__", "__LAST_CAID__", "__PREV_CAID__");

	private static final Set<String> ANDROID_IDS = Set.of("__OAID__", "__OAID_MD5__", "__IMEI__", "__IMEI_MD5__",
			"__ANDROIDID__", "__ANDROID_ID__", "__ANDROIDID_MD5__", "__AAID__");

	private static final Set<String> IOS_ID_PARAMETERS = Set.of("idfa", "idfa_md5", "caid", "caid_list", "caid1",
			"caid2", "last_caid", "prev_caid");

	private static final Set<String> ANDROID_ID_PARAMETERS = Set.of("oaid", "oaid_md5", "oaidmd5", "imei", "imei_md5",
			"imeimd5", "androidid", "android_id", "androidid_md5", "android_id_md5", "androidmd5", "aaid", "sid",
			"sid_md5", "sidmd5");

	public Resolution expandQueryTemplate(String template, FeedbackReq feedback, String callback, String bizId) {
		if (!StringUtils.hasText(template))
			return Resolution.rejected("TEMPLATE_URL_MISSING");
		int fragment = template.indexOf('#');
		String source = fragment < 0 ? template : template.substring(0, fragment);
		int query = source.indexOf('?');
		String path = query < 0 ? source : source.substring(0, query);
		if (MACRO.matcher(path).find())
			return Resolution.rejected("TEMPLATE_PATH_MACRO_UNSUPPORTED");

		Map<String, String> values = values(feedback, callback, bizId);
		Map<String, String> audit = new LinkedHashMap<>();
		Set<String> expanded = new LinkedHashSet<>();
		List<String> pairs = new ArrayList<>();
		if (query >= 0 && query < source.length() - 1) {
			for (String pair : source.substring(query + 1).split("&")) {
				String current = pair;
				Matcher matcher = MACRO.matcher(current);
				boolean omit = false;
				while (matcher.find()) {
					String macro = matcher.group();
					if (!values.containsKey(macro))
						return Resolution.rejected("UNKNOWN_MACRO:" + macro);
					String value = values.get(macro);
					if (!StringUtils.hasText(value)) {
						audit.put(macro, "OMITTED_EMPTY");
						omit = true;
						break;
					}
					current = current.replace(macro, NetUtil.strictUrlEncode(value, StandardCharsets.UTF_8));
					expanded.add(macro);
					audit.put(macro, "EXPANDED");
					matcher = MACRO.matcher(current);
				}
				if (!omit && StringUtils.hasText(current))
					pairs.add(current);
			}
		}
		String url = path + (pairs.isEmpty() ? "" : "?" + String.join("&", pairs));
		if (MACRO.matcher(url).find())
			return Resolution.rejected("RESIDUAL_MACRO");
		if (!expanded.contains("__CALLBACK__") && !expanded.contains("__CALLBACK_URL__"))
			return Resolution.rejected("CALLBACK_MACRO_MISSING");
		if (!hasFinalIdentity(feedback.getOs(), expanded))
			return Resolution.rejected("ATTRIBUTION_IDENTITY_MISSING");
		return new Resolution(url, null, audit);
	}

	public boolean hasInboundIdentity(FeedbackReq value) {
		boolean fallback = text(value.getIp()) && text(value.getUa());
		if (value.getOs() == OsTypeEnum.IOS) {
			return text(value.getIdfa(), value.getIdfaMd5(), value.getCaid(), value.getCaidList(), value.getCaid1(),
					value.getCaid2(), value.getLastCaid(), value.getPrevCaid()) || fallback;
		}
		if (value.getOs() == OsTypeEnum.ANDROID) {
			return text(value.getOaid(), value.getOaidMd5(), value.getImei(), value.getImeiMd5(), value.getAndroidid(),
					value.getAndroididMd5(), value.getAaid()) || fallback;
		}
		return fallback;
	}

	/** 校验协议实际生成的查询参数，而不是仅检查入站曾经出现过的字段。 */
	public boolean hasForwardedIdentity(String url, OsTypeEnum os) {
		if (!StringUtils.hasText(url))
			return false;
		try {
			String query = URI.create(url).getRawQuery();
			if (!StringUtils.hasText(query))
				return false;
			Set<String> present = new LinkedHashSet<>();
			for (String pair : query.split("&")) {
				int separator = pair.indexOf('=');
				if (separator > 0 && text(URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8))) {
					present.add(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8)
						.toLowerCase(Locale.ROOT));
				}
			}
			boolean fallback = (present.contains("ip") || present.contains("ipv4")) && present.contains("ua");
			if (os == OsTypeEnum.IOS) {
				return present.stream().anyMatch(IOS_ID_PARAMETERS::contains) || fallback;
			}
			if (os == OsTypeEnum.ANDROID) {
				return present.stream().anyMatch(ANDROID_ID_PARAMETERS::contains) || fallback;
			}
			return fallback;
		}
		catch (IllegalArgumentException error) {
			return false;
		}
	}

	/** 只记录模板宏的状态，不记录宏值；供协议专用展开器统一生成安全审计。 */
	public Map<String, String> auditTemplate(String template, String resolvedUrl) {
		if (!StringUtils.hasText(template))
			return Map.of();
		Set<String> forwardedKeys = queryKeys(resolvedUrl);
		Map<String, String> audit = new LinkedHashMap<>();
		int query = template.indexOf('?');
		String queryText = query < 0 ? "" : template.substring(query + 1).split("#", 2)[0];
		for (String pair : queryText.split("&")) {
			int separator = pair.indexOf('=');
			String key = separator < 0 ? pair : pair.substring(0, separator);
			boolean forwarded = forwardedKeys.contains(decode(key).toLowerCase(Locale.ROOT));
			Matcher matcher = MACRO.matcher(pair);
			while (matcher.find()) {
				audit.put(matcher.group(),
						!StringUtils.hasText(resolvedUrl) ? "BLOCKED" : forwarded ? "EXPANDED" : "OMITTED_EMPTY");
			}
		}
		return audit;
	}

	private Set<String> queryKeys(String url) {
		if (!StringUtils.hasText(url))
			return Set.of();
		try {
			String query = URI.create(url).getRawQuery();
			if (!StringUtils.hasText(query))
				return Set.of();
			Set<String> keys = new LinkedHashSet<>();
			for (String pair : query.split("&")) {
				int separator = pair.indexOf('=');
				keys.add(decode(separator < 0 ? pair : pair.substring(0, separator)).toLowerCase(Locale.ROOT));
			}
			return keys;
		}
		catch (IllegalArgumentException error) {
			return Set.of();
		}
	}

	private String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException error) {
			return value;
		}
	}

	private boolean hasFinalIdentity(OsTypeEnum os, Set<String> expanded) {
		boolean fallback = expanded.contains("__IP__") && expanded.contains("__UA__");
		if (os == OsTypeEnum.IOS)
			return expanded.stream().anyMatch(IOS_IDS::contains) || fallback;
		if (os == OsTypeEnum.ANDROID)
			return expanded.stream().anyMatch(ANDROID_IDS::contains) || fallback;
		return fallback;
	}

	private Map<String, String> values(FeedbackReq f, String callback, String bizId) {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("__REQUEST_ID__", f.getReqId());
		// OPPO monitoring templates use the shorter request aliases.
		m.put("__REQ__", f.getReqId());
		m.put("__RID__", f.getReqId());
		m.put("__CLICKID__", first(f.getClickId(), f.getReqId()));
		m.put("__IMEI__", f.getImei());
		m.put("__IMEI_MD5__", f.getImeiMd5());
		m.put("__OAID__", f.getOaid());
		m.put("__OAID_MD5__", f.getOaidMd5());
		m.put("__IDFA__", f.getIdfa());
		m.put("__IDFA_MD5__", f.getIdfaMd5());
		m.put("__CAID__", f.getCaid());
		m.put("__CAID_LIST__", first(f.getCaidList(), f.getCaid()));
		m.put("__CAID1__", first(f.getCaid1(), f.getCaid()));
		m.put("__CAID2__", f.getCaid2());
		m.put("__LAST_CAID__", f.getLastCaid());
		m.put("__PREV_CAID__", f.getPrevCaid());
		m.put("__AAID__", f.getAaid());
		m.put("__ANDROIDID__", f.getAndroidid());
		m.put("__ANDROID_ID__", f.getAndroidid());
		m.put("__ANDROIDID_MD5__", f.getAndroididMd5());
		m.put("__MAC__", f.getMac());
		m.put("__MAC_MD5__", f.getMacMd5());
		m.put("__IP__", f.getIp());
		m.put("__IPV6__", f.getIpv6());
		m.put("__UA__", f.getUa());
		m.put("__MODEL__", f.getModel());
		m.put("__OSV__", f.getOsv());
		m.put("__OS__", f.getOs() == OsTypeEnum.IOS ? "1" : f.getOs() == OsTypeEnum.ANDROID ? "0" : "3");
		m.put("__TS__", f.getTs());
		m.put("__MTS__", first(f.getTsMs(), f.getTs()));
		m.put("__CALLBACK__", callback);
		m.put("__CALLBACK_URL__", callback);
		m.put("__CAMPAIGNID__", f.getPlanId());
		m.put("__PLAN_ID__", f.getPlanId());
		m.put("__ADGROUPID__", f.getGroupId());
		m.put("__CREATIVEID__", f.getCreativeId());
		m.put("__ADVERTISERID__", f.getAdvId());
		m.put("__IMPRESSS_ID__", f.getImpresssId());
		m.put("__ORDER_PLAN_ID__", f.getPlanId());
		m.put("__ORDER_GROUP_ID__", f.getGroupId());
		m.put("__CREATIVE_ID__", f.getCreativeId());
		m.put("__ADVERTISER_ID__", f.getAdvId());
		m.put("__BIZ_ID__", bizId);
		return m;
	}

	private boolean text(String... values) {
		for (String v : values)
			if (StringUtils.hasText(v) && !MACRO.matcher(v).find())
				return true;
		return false;
	}

	private String first(String... values) {
		for (String v : values)
			if (StringUtils.hasText(v))
				return v;
		return null;
	}

	public record Resolution(String url, String rejectionCode, Map<String, String> audit) {
		public static Resolution rejected(String code) {
			return new Resolution(null, code, Map.of());
		}

		public boolean success() {
			return StringUtils.hasText(url);
		}
	}

}
