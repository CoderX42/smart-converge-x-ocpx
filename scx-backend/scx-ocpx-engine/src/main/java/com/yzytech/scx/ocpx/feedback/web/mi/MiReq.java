package com.yzytech.scx.ocpx.feedback.web.mi;

import com.yzytech.scx.ocpx.feedback.web.FeedbackRoutingRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 小米媒体 {@code /mi/feedback} 的完整查询参数模型（SGYZ mi 协议迁移）。
 *
 * <p>
 * 路由四参数使用 SCX 契约 {@code mId/tId/aId/lId/event}；方言字段按小米宏模板平移： callback_url=__CALLBACK_URL__&once_encode_sua=__ONCE_ENCODE_SUA__&caid=__CAID__&caid1=__CAID1__
 * &last_caid=__LAST_CAID__&prev_caid=__PREV_CAID__&idfa=__IDFA__&imei=__IMEI__&oaid=__OAID__&oaid_md5=__OAID_MD5__
 * &androidid=__ANDROIDID__&mac=__MAC__&ip=__IP__&os=__OS__&ts=__TS__&ts_ms=__TS_MS__&ua=__UA__&sign=__SIGN__
 * &adv_id=__ADVERTISER_ID__&group_id=__ORDER_GROUP_ID__&plan_id=__ORDER_PLAN_ID__&creative_id=__CREATIVE_ID__
 * &impresss_id=__IMPRESSS_ID__
 *
 * <p>
 * 媒体宏未替换时以 {@code __NAME__} 原样到达；自定义 getter 将这些占位值清洗为 {@code null}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiReq extends FeedbackRoutingRequest {

	private String request_id;

	private String callback_url;

	private String caid;

	private String caid1;

	private String last_caid;

	private String prev_caid;

	private String idfa;

	private String idfa_md5;

	private String imei;

	private String imei_md5;

	private String oaid;

	private String oaid_md5;

	private String androidid;

	private String mac;

	private String mac_md5;

	private String mac1_md5;

	private String ip;

	private String model;

	/** 0：Android 1：iOS 2：Windows Phone 3：其他。 */
	private String os;

	private String ts;

	private String mts;

	private String ua;

	private String sign;

	private String adv_id;

	private String group_id;

	private String plan_id;

	private String creative_id;

	private String impresss_id;

	/** ua 传递WKWebView的标准UA。 */
	private String once_encode_sua;

	private String callback;

	public String getCallback_url() {
		if (StringUtils.hasText(callback_url) && "__CALLBACK_URL__".equals(callback_url)) {
			return null;
		}
		return callback_url;
	}

	public String getRequest_id() {
		if (StringUtils.hasText(request_id) && "__REQUEST_ID__".equals(request_id)) {
			return null;
		}
		return request_id;
	}

	public String getCaid() {
		if (StringUtils.hasText(caid) && "__CAID__".equals(caid)) {
			return null;
		}
		return caid;
	}

	public String getCaid1() {
		if (StringUtils.hasText(caid1) && "__CAID1__".equals(caid1)) {
			return null;
		}
		return caid1;
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
		if (StringUtils.hasText(androidid) && "__ANDROIDID__".equals(androidid)) {
			return null;
		}
		return androidid;
	}

	public String getMac() {
		if (StringUtils.hasText(mac) && "__MAC__".equals(mac)) {
			return null;
		}
		return mac;
	}

	public String getIp() {
		if (StringUtils.hasText(ip) && "__IP__".equals(ip)) {
			return null;
		}
		return ip;
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

	public String getCallback() {
		if (StringUtils.hasText(callback) && "__CALLBACK__".equals(callback)) {
			return null;
		}
		return callback;
	}

}
