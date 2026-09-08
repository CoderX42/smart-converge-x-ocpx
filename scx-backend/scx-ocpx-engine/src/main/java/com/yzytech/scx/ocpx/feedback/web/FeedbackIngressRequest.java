package com.yzytech.scx.ocpx.feedback.web;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * {@code /feedback} 的完整查询参数模型。
 *
 * <p>
 * 媒体宏未替换时常以 {@code __NAME__} 原样到达；自定义 getter 将这些占位值清洗为 {@code null}， 避免把宏文本当作真实设备标识或回调地址。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackIngressRequest extends FeedbackRoutingRequest {

	private static final Pattern UNEXPANDED_REQUEST_ID = Pattern.compile("__[A-Za-z][A-Za-z0-9_]*__");

	/** 请求唯一标识和媒体回调地址。 */
	private String request_id;

	private String rtaRequestId;

	/** Phoenix 可选投放版位标识；同时接受 tagid 与常见驼峰别名 tagId。 */
	private String tagid;

	private String tagId;

	private String callback_url;

	/** 设备标识及其散列或历史版本。 */
	private String caid;

	private String caid_list;

	private String caid1;

	private String caid2;

	private String last_caid;

	private String prev_caid;

	private String idfa;

	private String idfa_md5;

	private String aaid;

	private String imei;

	private String imei_md5;

	private String oaid;

	private String oaid_md5;

	private String androidid;

	private String androidid_md5;

	private String mac;

	private String mac_md5;

	private String mac1_md5;

	/** 设备网络、型号、系统、时间和 User-Agent。 */
	private String ip;

	private String ipv6;

	private String model;

	private String osv;

	private String os;

	private String ts;

	private String mts;

	private String ua;

	/** 媒体签名及广告层级标识。 */
	private String sign;

	private String adv_id;

	private String group_id;

	private String plan_id;

	private String creative_id;

	private String impresss_id;

	private String clickid;

	/** 兼容旧媒体协议的扩展参数，当前通用适配器不消费。 */
	private String once_encode_sua;

	/** 返回清洗后的回调地址；未替换的宏视为缺失值。 */
	public String getCallback_url() {
		if (StringUtils.hasText(callback_url)
				&& ("__CALLBACK_URL__".equals(callback_url) || "__CALLBACK__".equals(callback_url))) {
			return null;
		}
		return callback_url;
	}

	/** 返回清洗后的请求 ID；未替换的宏将触发适配器的 MD5 回退逻辑。 */
	public String getRequest_id() {
		if (StringUtils.hasText(request_id) && UNEXPANDED_REQUEST_ID.matcher(request_id.trim()).find()) {
			return null;
		}
		return request_id;
	}

	public String getTagid() {
		return cleanMacro(tagid, "__TAGID__");
	}

	public String getTagId() {
		return cleanMacro(tagId, "__TAGID__");
	}

	/** 返回两个别名中第一个实际值。 */
	public String resolvedTagId() {
		return StringUtils.hasText(getTagid()) ? getTagid() : getTagId();
	}

	private String cleanMacro(String value, String macro) {
		return StringUtils.hasText(value) && macro.equalsIgnoreCase(value.trim()) ? null : value;
	}

	public String getCaid() {
		if (StringUtils.hasText(caid) && "__CAID__".equals(caid)) {
			return null;
		}
		return caid;
	}

	public String getCaid_list() {
		if (StringUtils.hasText(caid_list) && "__CAID_LIST__".equals(caid_list)) {
			return null;
		}
		return caid_list;
	}

	public String getCaid1() {
		if (StringUtils.hasText(caid1) && "__CAID1__".equals(caid1)) {
			return null;
		}
		return caid1;
	}

	public String getCaid2() {
		if (StringUtils.hasText(caid2) && "__CAID2__".equals(caid2)) {
			return null;
		}
		return caid2;
	}

	public String getLast_caid() {
		if (StringUtils.hasText(last_caid) && "__LAST_CAID__".equals(last_caid)) {
			return null;
		}
		return last_caid;
	}

	public String getPrev_caid() {
		if (StringUtils.hasText(prev_caid) && "__PREV_CAID__".equals(prev_caid)) {
			return null;
		}
		return prev_caid;
	}

	public String getIdfa() {
		if (StringUtils.hasText(idfa) && "__IDFA__".equals(idfa)) {
			return null;
		}
		return idfa;
	}

	public String getIdfa_md5() {
		if (StringUtils.hasText(idfa_md5) && "__IDFA_MD5__".equals(idfa_md5)) {
			return null;
		}
		return idfa_md5;
	}

	public String getAaid() {
		if (StringUtils.hasText(aaid) && "__AAID__".equals(aaid)) {
			return null;
		}
		return aaid;
	}

	public String getImei() {
		if (StringUtils.hasText(imei) && "__IMEI__".equals(imei)) {
			return null;
		}
		return imei;
	}

	public String getOaid() {
		if (StringUtils.hasText(oaid) && "__OAID__".equals(oaid)) {
			return null;
		}
		return oaid;
	}

	public String getOaid_md5() {
		if (StringUtils.hasText(oaid_md5) && "__OAID_MD5__".equals(oaid_md5)) {
			return null;
		}
		return oaid_md5;
	}

	public String getAndroidid() {
		if (StringUtils.hasText(androidid)
				&& ("__ANDROIDID__".equals(androidid) || "__ANDROID_ID__".equals(androidid))) {
			return null;
		}
		return androidid;
	}

	public String getAndroidid_md5() {
		if (StringUtils.hasText(androidid_md5) && "__ANDROIDID_MD5__".equals(androidid_md5)) {
			return null;
		}
		return androidid_md5;
	}

	public String getMac() {
		if (StringUtils.hasText(mac) && "__MAC__".equals(mac)) {
			return null;
		}
		return mac;
	}

	public String getMac_md5() {
		if (StringUtils.hasText(mac_md5) && "__MAC_MD5__".equals(mac_md5)) {
			return null;
		}
		return mac_md5;
	}

	/** 未清洗的 request_id，仅供正式点击入口执行占位符和长度校验。 */
	public String rawRequestId() {
		return request_id;
	}

	public String getIp() {
		if (StringUtils.hasText(ip) && "__IP__".equals(ip)) {
			return null;
		}
		return ip;
	}

	public String getIpv6() {
		if (StringUtils.hasText(ipv6) && "__IPV6__".equals(ipv6)) {
			return null;
		}
		return ipv6;
	}

	public String getOsv() {
		if (StringUtils.hasText(osv) && "__OSV__".equals(osv)) {
			return null;
		}
		return osv;
	}

	public String getModel() {
		if (StringUtils.hasText(model) && "__MODEL__".equals(model)) {
			return null;
		}
		return model;
	}

	public String getOs() {
		if (StringUtils.hasText(os) && "__OS__".equals(os)) {
			return null;
		}
		return os;
	}

	public String getTs() {
		if (StringUtils.hasText(ts) && "__TS__".equals(ts)) {
			return null;
		}
		return ts;
	}

	public String getTs_ms() {
		// 旧协议约定 MTS 宏未替换时由接收端补当前毫秒时间。
		if (StringUtils.hasText(mts) && "__MTS__".equals(mts)) {
			return String.valueOf(System.currentTimeMillis());
		}
		return mts;
	}

	public String getUa() {
		if (StringUtils.hasText(ua) && "__UA__".equals(ua)) {
			return null;
		}
		return ua;
	}

	public String getSign() {
		if (StringUtils.hasText(sign) && "__SIGN__".equals(sign)) {
			return null;
		}
		return sign;
	}

	public String getAdv_id() {
		if (StringUtils.hasText(adv_id) && "__ADVERTISER_ID__".equals(adv_id)) {
			return null;
		}
		return adv_id;
	}

	public String getGroup_id() {
		if (StringUtils.hasText(group_id) && "__ORDER_GROUP_ID__".equals(group_id)) {
			return null;
		}
		return group_id;
	}

	public String getPlan_id() {
		if (StringUtils.hasText(plan_id) && "__ORDER_PLAN_ID__".equals(plan_id)) {
			return null;
		}
		return plan_id;
	}

	public String getCreative_id() {
		if (StringUtils.hasText(creative_id) && "__CREATIVE_ID__".equals(creative_id)) {
			return null;
		}
		return creative_id;
	}

	public String getImpresss_id() {
		if (StringUtils.hasText(impresss_id) && "__IMPRESSS_ID__".equals(impresss_id)) {
			return null;
		}
		return impresss_id;
	}

	public String getClickid() {
		if (StringUtils.hasText(clickid) && "__CLICKID__".equals(clickid)) {
			return null;
		}
		return clickid;
	}

}
