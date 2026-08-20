package com.yzytech.scx.ocpx.admin.service;

import org.springframework.stereotype.Component;

import java.util.List;

/** Feedback v2 的单一媒体快捷宏目录。 */
@Component
public class MediaMacroCatalog {

	private static final List<Macro> MACROS = List.of(macro("caid", "__CAID__", "CAID", "设备标识", "IOS", true),
			macro("caid_list", "__CAID_LIST__", "CAID 列表", "设备标识", "IOS", true),
			macro("caid1", "__CAID1__", "CAID1", "设备标识", "IOS", true),
			macro("caid2", "__CAID2__", "CAID2", "设备标识", "IOS", true),
			macro("last_caid", "__LAST_CAID__", "历史 CAID", "设备标识", "IOS", true),
			macro("prev_caid", "__PREV_CAID__", "上一 CAID", "设备标识", "IOS", true),
			macro("idfa", "__IDFA__", "IDFA", "设备标识", "IOS", true),
			macro("idfa_md5", "__IDFA_MD5__", "IDFA MD5", "设备标识", "IOS", true),
			macro("aaid", "__AAID__", "AAID", "设备标识", "ANDROID", true),
			macro("imei", "__IMEI__", "IMEI", "设备标识", "ANDROID", true),
			macro("imei_md5", "__IMEI_MD5__", "IMEI MD5", "设备标识", "ANDROID", true),
			macro("oaid", "__OAID__", "OAID", "设备标识", "ANDROID", true),
			macro("oaid_md5", "__OAID_MD5__", "OAID MD5", "设备标识", "ANDROID", true),
			macro("androidid", "__ANDROIDID__", "Android ID", "设备标识", "ANDROID", true),
			macro("androidid_md5", "__ANDROIDID_MD5__", "Android ID MD5", "设备标识", "ANDROID", true),
			macro("mac", "__MAC__", "MAC", "设备标识", "BOTH", false),
			macro("mac_md5", "__MAC_MD5__", "MAC MD5", "设备标识", "BOTH", false),
			macro("ip", "__IP__", "IPv4", "网络/终端", "BOTH", false),
			macro("ipv6", "__IPV6__", "IPv6", "网络/终端", "BOTH", false),
			macro("ua", "__UA__", "User-Agent", "网络/终端", "BOTH", false),
			macro("os", "__OS__", "操作系统", "网络/终端", "BOTH", false),
			macro("osv", "__OSV__", "系统版本", "网络/终端", "BOTH", false),
			macro("model", "__MODEL__", "机型", "网络/终端", "BOTH", false),
			macro("ts", "__TS__", "秒时间戳", "网络/终端", "BOTH", false),
			macro("mts", "__MTS__", "毫秒时间戳", "网络/终端", "BOTH", false),
			macro("request_id", "__REQUEST_ID__", "请求 ID", "平台路由", "BOTH", false),
			macro("callback_url", "__CALLBACK_URL__", "回调地址", "平台路由", "BOTH", false),
			macro("clickid", "__CLICKID__", "点击 ID", "平台路由", "BOTH", false),
			macro("impresss_id", "__IMPRESSS_ID__", "曝光 ID", "平台路由", "BOTH", false),
			macro("plan_id", "__ORDER_PLAN_ID__", "广告计划", "广告维度", "BOTH", false),
			macro("group_id", "__ORDER_GROUP_ID__", "广告组", "广告维度", "BOTH", false),
			macro("creative_id", "__CREATIVE_ID__", "创意", "广告维度", "BOTH", false),
			macro("adv_id", "__ADVERTISER_ID__", "广告主", "广告维度", "BOTH", false));

	public List<Macro> list() {
		return MACROS;
	}

	private static Macro macro(String key, String macro, String label, String group, String os, boolean identity) {
		return new Macro(key, macro, label, group, os, identity);
	}

	public record Macro(String key, String macro, String label, String group, String osSupport,
			boolean attributionIdentity) {
	}

}
