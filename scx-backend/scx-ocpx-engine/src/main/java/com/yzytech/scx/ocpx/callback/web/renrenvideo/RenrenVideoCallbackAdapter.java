package com.yzytech.scx.ocpx.callback.web.renrenvideo;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/** 人人视频字符串事件与认证 Feedback 上下文适配器。 */
@Component
@RequiredArgsConstructor
public class RenrenVideoCallbackAdapter {

	private static final Map<String, ActionTypeEnum> ACTIONS = Map.of("activate", ActionTypeEnum.ACTIVATE, "register",
			ActionTypeEnum.REGISTER, "leave", ActionTypeEnum.NEXT_STAY, "pay", ActionTypeEnum.PAYMENT, "first_activate",
			ActionTypeEnum.FIRST_ACTIVATION);

	private final OcpxKeyRingCrypto callbackCrypto;

	public CallbackReq toCallback(RenrenVideoCallbackRequest request) {
		if (request == null || !StringUtils.hasText(request.getKid()) || !StringUtils.hasText(request.getExt())
				|| !StringUtils.hasText(request.getEvent()) || request.getKid().length() > 32
				|| request.getExt().length() > 12000 || request.getEvent().length() > 32) {
			throw new IllegalArgumentException("missing RENREN_VIDEO callback parameter");
		}
		String partnerEvent = request.getEvent().trim().toLowerCase(Locale.ROOT);
		ActionTypeEnum actionType = ACTIONS.get(partnerEvent);
		if (actionType == null) {
			throw new IllegalArgumentException("unknown RENREN_VIDEO event");
		}
		String clearText = callbackCrypto.decryptCallback(request.getKid(), request.getExt());
		if (!StringUtils.hasText(clearText)) {
			throw new IllegalArgumentException("invalid RENREN_VIDEO callback ext");
		}
		try {
			FeedbackReq feedback = JSON.parseObject(clearText, FeedbackReq.class);
			requireRouteIds(feedback);
			return CallbackReq.builder()
				.feedback(feedback)
				.actionType(actionType)
				.actionTime(System.currentTimeMillis())
				.partnerActionType(partnerEvent)
				.currencyAmount(null)
				.build();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("invalid RENREN_VIDEO callback ext", exception);
		}
	}

	private void requireRouteIds(FeedbackReq feedback) {
		if (feedback == null) {
			throw new IllegalArgumentException("callback ext is missing SCX route ids");
		}
		requireRouteId(feedback.getMId(), "mId");
		requireRouteId(feedback.getTId(), "tId");
		requireRouteId(feedback.getAId(), "aId");
		requireRouteId(feedback.getLId(), "lId");
		if (!StringUtils.hasText(feedback.getReqId()) || feedback.getReqId().length() > 128) {
			throw new IllegalArgumentException("callback ext has invalid reqId");
		}
	}

	private void requireRouteId(String value, String name) {
		if (!StringUtils.hasText(value) || value.length() > 32) {
			throw new IllegalArgumentException("callback ext has invalid SCX route id: " + name);
		}
	}

}
