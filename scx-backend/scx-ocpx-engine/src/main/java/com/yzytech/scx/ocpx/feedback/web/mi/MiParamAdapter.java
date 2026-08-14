package com.yzytech.scx.ocpx.feedback.web.mi;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/** 将小米 Feedback Query 静态映射为 Engine 内部 {@link FeedbackReq}（SGYZ MiParamAdapter 平移）。 */
public final class MiParamAdapter {

	private MiParamAdapter() {
	}

	/**
	 * 标准化小米请求：UA 取 {@code once_encode_sua}、机型取原始 {@code ua}，请求 ID 缺失时回退为整包 JSON 的
	 * MD5，回调地址非空时执行严格 URL 编码。
	 * @param req Spring MVC 绑定的小米查询参数
	 * @return 可供去重、报表和上游转发使用的统一 Feedback
	 */
	public static FeedbackReq getFeedbackReq(MiReq req) {
		// 在字段清洗前序列化完整请求，保证缺失 request_id 时的回退值稳定可复现。
		String jsonString = JSON.toJSONString(req);
		FeedbackReq data = FeedbackReq.builder()
			.mId(req.getMId())
			.tId(req.getTId())
			.aId(req.getAId())
			.lId(req.getLId())
			.event(EventTypeEnum.getEventTypeEnum(req.getEvent()))
			.reqId(req.getRequest_id())
			.callbackUrl(req.getCallback_url())
			.caid(req.getCaid())
			.caidList(req.getCaid_list())
			.caid1(req.getCaid1())
			.idfa(req.getIdfa())
			.idfaMd5(req.getIdfa_md5())
			.aaid(req.getAaid())
			.imei(req.getImei())
			.imeiMd5(req.getImei_md5())
			.oaid(req.getOaid())
			.oaidMd5(req.getOaid_md5())
			.androidid(req.getAndroidid())
			.mac(req.getMac())
			.ip(req.getIp())
			.ts(req.getTs())
			.tsMs(req.getTs_ms())
			.ua(req.getOnce_encode_sua())
			.model(req.getUa())
			.advId(req.getAdv_id())
			.groupId(req.getGroup_id())
			.planId(req.getPlan_id())
			.creativeId(req.getCreative_id())
			.impresssId(req.getImpresss_id())
			.clickId(req.getClickid())
			.os(matchOs(req.getOs()))
			.ext(req.getCallback())
			.build();
		if (!StringUtils.hasText(data.getReqId())) {
			data.setReqId(DigestUtils.md5DigestAsHex(jsonString.getBytes()));
		}
		if (StringUtils.hasText(data.getCallbackUrl())) {
			data.setCallbackUrl(NetUtil.strictUrlEncode(data.getCallbackUrl(), StandardCharsets.UTF_8));
		}
		return data;
	}

	private static OsTypeEnum matchOs(String os) {
		if (!StringUtils.hasText(os)) {
			return OsTypeEnum.ANDROID;
		}
		return switch (os) {
			case "0" -> OsTypeEnum.ANDROID;
			case "1" -> OsTypeEnum.IOS;
			default -> OsTypeEnum.UNKNOWN;
		};
	}

}
