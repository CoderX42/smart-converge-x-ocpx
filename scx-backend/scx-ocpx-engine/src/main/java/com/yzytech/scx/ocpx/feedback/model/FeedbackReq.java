package com.yzytech.scx.ocpx.feedback.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yzytech.scx.ocpx.shared.enums.DeviceIdTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Engine 内部统一的媒体 Feedback 模型。
 *
 * <p>
 * 该对象由不同媒体协议的入站 DTO 适配而来，随后同时用于去重、报表记录、广告主 URL 宏替换， 并加密写入 Callback 的 {@code ext} 参数。四个业务
 * ID 均为 SCX 字符串主键。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackReq {

	/** 下游媒体主键，对应 {@code OcpxMedia.id}。 */
	@JSONField(name = "mId")
	private String mId;

	/** 监测任务主键，对应 {@code OcpxMonitorTask.id}。 */
	@JSONField(name = "tId")
	private String tId;

	/** 上游广告主主键，对应 {@code OcpxAdvertiser.id}；不是广告主侧广告 ID {@code advId}。 */
	@JSONField(name = "aId")
	private String aId;

	/** 推广链接主键，对应 {@code OcpxPromotionLink.id}。 */
	@JSONField(name = "lId")
	private String lId;

	/** 标准化事件及 Engine 接收时间。 */
	private EventTypeEnum event;

	private String timeKeyRes;

	/** 曝光、去重曝光、点击和去重点击的本条增量指标。 */
	private int showSmt;

	private int uniShowSmt;

	private int clkSmt;

	private int uniClkSmt;

	/** 下游媒体提供的回调地址；入站适配后以严格 URL 编码形式保存。 */
	private String callbackUrl;

	/** 设备标识与设备环境信息。 */
	private String caid;

	private String caidList;

	private String caidVersion;

	private String caid1;

	private String caid2;

	private String lastCaid;

	private String prevCaid;

	private String idfa;

	private String idfaMd5;

	private String aaid;

	private String imei;

	private String imeiMd5;

	private String oaid;

	private String oaidMd5;

	private String androidid;

	private String androididMd5;

	private String mac;

	private String macMd5;

	private String ip;

	private String ipv6;

	private OsTypeEnum os;

	private String ts;

	private String tsMs;

	private String ua;

	/** 广告主侧的广告、组、计划、创意、曝光和点击标识。 */
	private String advId;

	private String groupId;

	private String planId;

	private String creativeId;

	private String impresssId;

	private String clickId;

	/** 本次请求唯一标识；正式点击必须由媒体提供具体值，曝光保留兼容回退。 */
	private String reqId;

	/**
	 * 入站适配器是否因缺少/未替换 request_id 而生成了回退值。该标记只存在于内存， 不得进入事实、日志、Redis 或下游请求。
	 */
	@JSONField(serialize = false, deserialize = false)
	@JsonIgnore
	private boolean requestIdGenerated;

	/**
	 * This feedback belongs to an RTA-enabled task. The marker is process-local and lets
	 * compatibility/logging paths redact raw device identifiers after admission.
	 */
	@JSONField(serialize = false, deserialize = false)
	@JsonIgnore
	private boolean rtaProtected;

	private String rtaRequestId;

	/**
	 * Server-selected media protocol; only carried through the encrypted callback
	 * context.
	 */
	@JSONField(serialize = true, deserialize = true)
	private String downstreamProtocol;

	@JSONField(serialize = true, deserialize = true)
	private String downstreamCredentialRef;

	@JSONField(serialize = true, deserialize = true)
	private String oppoPkg;

	/**
	 * Encrypted transient click identifier; never enters the callback envelope or JSON.
	 */
	@JsonIgnore
	@JSONField(serialize = false, deserialize = false)
	private String oppoClickDevice;

	@JSONField(serialize = false, deserialize = false)
	@JsonIgnore
	private String providerRtaRequestId;

	@JSONField(serialize = false, deserialize = false)
	@JsonIgnore
	private java.util.Map<String, String> rtaRouteConfig;

	/**
	 * Authenticated callback-context marker. The process-local {@link #rtaProtected} bit
	 * deliberately stays out of ordinary JSON, while a detached RTA dispatch snapshot
	 * carries this non-sensitive marker inside the encrypted callback context so a later
	 * Callback can apply the same redaction policy. Jackson keeps the marker hidden from
	 * management/API responses.
	 */
	@JSONField(name = "rta_protected", serialize = true, deserialize = true)
	private Boolean callbackRtaProtected;

	/** Phoenix 可选投放版位标识（TAGID）。 */
	private String tagId;

	private String marke;

	private String model;

	private String osv;

	/** 合作方扩展数据及抽样日志标记。 */
	private String ext;

	private boolean flagLog;

	/** 按优先级从设备标识中推导的统一用户标识及其类型。 */
	private String userId;

	private DeviceIdTypeEnum userType;

	/**
	 * Create the only form of a RTA-protected Feedback that may enter an advertiser
	 * delivery worker. The original object is never mutated: the raw identifiers are
	 * cleared on the copy, while their already-supplied hashes remain available to
	 * protocols that can operate on hashed IDs. The callback URL is retained only when
	 * its decoded representation contains none of the inbound raw identifiers; an unsafe
	 * or malformed URL is dropped. The opaque callback envelope is always rebuilt by the
	 * protocol adapter.
	 *
	 * <p>
	 * The Weifeng RTA adapter receives the original object before this method is called.
	 * No other upstream protocol is an approved raw-identifier destination.
	 * </p>
	 * @return an immutable-by-convention, raw-identifier-free dispatch snapshot
	 */
	public FeedbackReq copyForRtaDownstream() {
		FeedbackReq copy = new FeedbackReq();
		copy.mId = mId;
		copy.tId = tId;
		copy.aId = aId;
		copy.lId = lId;
		copy.event = event;
		copy.timeKeyRes = timeKeyRes;
		copy.showSmt = showSmt;
		copy.uniShowSmt = uniShowSmt;
		copy.clkSmt = clkSmt;
		copy.uniClkSmt = uniClkSmt;
		copy.callbackUrl = safeCallbackUrlForRta();
		copy.caidVersion = caidVersion;
		copy.idfaMd5 = idfaMd5;
		copy.imeiMd5 = imeiMd5;
		copy.oaidMd5 = oaidMd5;
		copy.androididMd5 = androididMd5;
		copy.macMd5 = macMd5;
		copy.ip = ip;
		copy.ipv6 = ipv6;
		copy.os = os;
		copy.ts = ts;
		copy.tsMs = tsMs;
		copy.ua = ua;
		copy.advId = advId;
		copy.groupId = groupId;
		copy.planId = planId;
		copy.creativeId = creativeId;
		copy.impresssId = impresssId;
		copy.clickId = clickId;
		copy.reqId = reqId;
		copy.rtaRequestId = rtaRequestId;
		copy.downstreamProtocol = downstreamProtocol;
		copy.downstreamCredentialRef = downstreamCredentialRef;
		copy.oppoPkg = oppoPkg;
		copy.oppoClickDevice = oppoClickDevice;
		copy.providerRtaRequestId = providerRtaRequestId;
		copy.rtaRouteConfig = rtaRouteConfig;
		copy.requestIdGenerated = requestIdGenerated;
		copy.rtaProtected = true;
		copy.callbackRtaProtected = true;
		copy.tagId = tagId;
		copy.marke = marke;
		copy.model = model;
		copy.osv = osv;
		copy.flagLog = flagLog;
		copy.userType = userType;
		// ext and every raw identifier intentionally remain null. A callback URL is
		// retained only when its decoded form contains none of the inbound raw device
		// values; otherwise the whole URL is dropped rather than risking a leak.
		return copy;
	}

	/**
	 * Returns whether this object was marked as RTA protected either in-process or by the
	 * authenticated callback context. The latter is intentionally fail-safe: callers only
	 * use the bit to redact/withhold data, never to permit an otherwise forbidden
	 * operation.
	 */
	public boolean isRtaProtected() {
		return rtaProtected || Boolean.TRUE.equals(callbackRtaProtected);
	}

	private String safeCallbackUrlForRta() {
		if (!StringUtils.hasText(callbackUrl)) {
			return callbackUrl;
		}
		String decoded = callbackUrl;
		try {
			// Ingress normally encodes once; decode a bounded number of times so a
			// doubly-encoded device value cannot evade the scan. A malformed escape
			// fails closed by dropping the callback destination.
			for (int i = 0; i < 3; i++) {
				String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
				if (next.equals(decoded)) {
					break;
				}
				decoded = next;
			}
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
		try {
			URI uri = URI.create(decoded);
			if (uri.getUserInfo() != null || uri.getFragment() != null
					|| uri.getQuery() == null && decoded.contains("?")) {
				return null;
			}
			String query = uri.getRawQuery();
			if (query != null) {
				for (String parameter : query.split("&", -1)) {
					String name = parameter;
					int equals = parameter.indexOf('=');
					if (equals >= 0) {
						name = parameter.substring(0, equals);
					}
					name = URLDecoder.decode(name, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).trim();
					if (name.matches(
							"(?:idfa|imei|oaid|androidid|android_id|aaid|caid|caid_new|mac|device|userid|user_id|device_id|advertising_id|idfa_md5sum|imei_md5sum|oaid_md5sum|android_id_md5sum|mac_md5sum)")) {
						return null;
					}
				}
			}
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
		String haystack = decoded.toLowerCase(Locale.ROOT);
		for (String raw : rawDeviceValues()) {
			if (StringUtils.hasText(raw) && !isMacro(raw) && haystack.contains(raw.toLowerCase(Locale.ROOT))) {
				return null;
			}
		}
		return callbackUrl;
	}

	private List<String> rawDeviceValues() {
		List<String> values = new ArrayList<>();
		values.add(caid);
		values.add(caidList);
		values.add(caid1);
		values.add(caid2);
		values.add(lastCaid);
		values.add(prevCaid);
		values.add(idfa);
		values.add(aaid);
		values.add(imei);
		values.add(oaid);
		values.add(androidid);
		values.add(mac);
		values.add(userId);
		return values;
	}

	private boolean isMacro(String value) {
		return value.startsWith("__") && value.endsWith("__");
	}

	/**
	 * 按 OAID、CAID、IDFA、IMEI 的优先级选择统一用户标识，并同步更新 {@code userType}。
	 * @return 首个非空设备标识；均为空时返回空字符串
	 */
	public String getUserId() {
		if (StringUtils.hasText(oaid)) {
			userType = DeviceIdTypeEnum.OAID;
			return oaid;
		}
		if (StringUtils.hasText(caid)) {
			userType = DeviceIdTypeEnum.CAID;
			return caid;
		}
		if (StringUtils.hasText(idfa)) {
			userType = DeviceIdTypeEnum.IDFA;
			return idfa;
		}
		if (StringUtils.hasText(imei)) {
			userType = DeviceIdTypeEnum.IMEI;
			return imei;
		}
		return "";
	}

}
