package com.yzytech.scx.ocpx.controller.general;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.constant.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.constant.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.controller.general.dto.GeneralReq;
import com.yzytech.scx.ocpx.util.NetUtil;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

public final class GeneralParamAdapter {

	private GeneralParamAdapter() {
	}

	public static FeedbackReq getFeedbackReq(GeneralReq request) {
		String json = JSON.toJSONString(request);
		FeedbackReq data = FeedbackReq.builder()
			.chid(request.getChid())
			.mid(request.getMid())
			.dspid(request.getDspid())
			.linkid(request.getLinkid())
			.event(EventTypeEnum.getEventTypeEnum(request.getEvent()))
			.reqId(request.getRequest_id())
			.callbackUrl(request.getCallback_url())
			.caid(request.getCaid())
			.caid1(request.getCaid1())
			.idfa(request.getIdfa())
			.imei(request.getImei())
			.imeiMd5(request.getImei_md5())
			.oaid(request.getOaid())
			.oaidMd5(request.getOaid_md5())
			.androidid(request.getAndroidid())
			.mac(request.getMac())
			.ip(request.getIp())
			.ts(request.getTs())
			.tsMs(request.getTs_ms())
			.ua(request.getUa())
			.model(request.getModel())
			.advId(request.getAdv_id())
			.groupId(request.getGroup_id())
			.planId(request.getPlan_id())
			.creativeId(request.getCreative_id())
			.impresssId(request.getImpresss_id())
			.os(matchOs(request.getOs()))
			.build();
		if (!StringUtils.hasText(data.getReqId())) {
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
		return switch (os) {
			case "0" -> OsTypeEnum.ANDROID;
			case "1" -> OsTypeEnum.IOS;
			default -> OsTypeEnum.UNKNOWN;
		};
	}

}
