package com.yzytech.scx.ocpx.feedback.web;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 将媒体 Feedback Query 转换为 Engine 内部 {@link FeedbackReq}。 */
@Component
public class FeedbackIngressAdapter implements FeedbackRequestAdapter {

	/**
	 * 标准化媒体请求，补齐请求 ID，并对回调地址执行严格 URL 编码。
	 * @param request Spring MVC 绑定的通用查询参数
	 * @return 可供去重、报表和上游转发使用的统一 Feedback
	 */
	@Override
	public FeedbackReq toFeedback(FeedbackIngressRequest request) {
		// 在字段清洗前序列化完整请求，保证缺失 request_id 时的回退值稳定可复现。
		String json = JSON.toJSONString(request);
		FeedbackReq data = FeedbackReq.builder()
			.mId(request.getMId())
			.tId(request.getTId())
			.aId(request.getAId())
			.lId(request.getLId())
			.event(EventTypeEnum.getEventTypeEnum(request.getEvent()))
			.reqId(request.getRequest_id())
			.callbackUrl(request.getCallback_url())
			.caid(request.getCaid())
			.caidList(request.getCaid_list())
			.caid1(request.getCaid1())
			.caid2(request.getCaid2())
			.lastCaid(request.getLast_caid())
			.prevCaid(request.getPrev_caid())
			.idfa(request.getIdfa())
			.idfaMd5(request.getIdfa_md5())
			.aaid(request.getAaid())
			.imei(request.getImei())
			.imeiMd5(request.getImei_md5())
			.oaid(request.getOaid())
			.oaidMd5(request.getOaid_md5())
			.androidid(request.getAndroidid())
			.androididMd5(request.getAndroidid_md5())
			.mac(request.getMac())
			.ip(request.getIp())
			.ipv6(request.getIpv6())
			.ts(request.getTs())
			.tsMs(request.getTs_ms())
			.ua(request.getUa())
			.model(request.getModel())
			.osv(request.getOsv())
			.advId(request.getAdv_id())
			.groupId(request.getGroup_id())
			.planId(request.getPlan_id())
			.creativeId(request.getCreative_id())
			.impresssId(request.getImpresss_id())
			.clickId(request.getClickid())
			.os(matchOs(request.getOs()))
			.build();
		if (!StringUtils.hasText(data.getReqId())) {
			// 旧协议允许不传 request_id，此时以完整请求 JSON 的 MD5 作为去重键。
			data.setReqId(DigestUtils.md5DigestAsHex(json.getBytes()));
		}
		if (StringUtils.hasText(data.getCallbackUrl())) {
			data.setCallbackUrl(NetUtil.strictUrlEncode(data.getCallbackUrl(), StandardCharsets.UTF_8));
		}
		return data;
	}

	private static OsTypeEnum matchOs(String os) {
		if (!StringUtils.hasText(os)) {
			return OsTypeEnum.UNKNOWN;
		}
		return switch (os.trim().toUpperCase(Locale.ROOT)) {
			case "0", "ANDROID" -> OsTypeEnum.ANDROID;
			case "1", "IOS" -> OsTypeEnum.IOS;
			default -> OsTypeEnum.UNKNOWN;
		};
	}

}
