package com.yzytech.scx.ocpx.callback.web.aerim;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/** AERIM 字符串动作与 AES Feedback 上下文适配器。 */
@Component
public class AerimCallbackAdapter {

	private static final Map<String, ActionTypeEnum> ACTIONS = Map.ofEntries(
			Map.entry("activate", ActionTypeEnum.ACTIVATE), Map.entry("reactivation", ActionTypeEnum.EVOKE),
			Map.entry("register", ActionTypeEnum.REGISTER), Map.entry("purchase", ActionTypeEnum.PAYMENT),
			Map.entry("new", ActionTypeEnum.CUSTOMER_ACQUISITION), Map.entry("day1retention", ActionTypeEnum.NEXT_STAY),
			Map.entry("completeorder", ActionTypeEnum.FINISH_THE_ORDER),
			Map.entry("firstreactivation", ActionTypeEnum.FIRST_ACTIVATION),
			Map.entry("firstorder", ActionTypeEnum.FIRST_ORDER));

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	public CallbackReq toCallback(AerimCallbackRequest request) {
		if (request == null || !StringUtils.hasText(request.getExt()) || !StringUtils.hasText(request.getS())
				|| !StringUtils.hasText(request.getAction_type())) {
			throw new IllegalArgumentException("missing AERIM callback parameter");
		}
		ActionTypeEnum actionType = ACTIONS.get(request.getAction_type());
		if (actionType == null) {
			throw new IllegalArgumentException("unknown AERIM action_type");
		}
		validateAmount(request.getCurrency_amount(), actionType);
		String clearText = AesUtil.decryptBase64(request.getExt(), callbackAesKey);
		if (!StringUtils.hasText(clearText)) {
			throw new IllegalArgumentException("invalid AERIM callback ext");
		}
		try {
			FeedbackReq feedback = JSON.parseObject(clearText, FeedbackReq.class);
			requireRouteIds(feedback);
			return CallbackReq.builder()
				.feedback(feedback)
				.actionType(actionType)
				.actionTime(System.currentTimeMillis())
				.partnerActionType(request.getAction_type())
				.currencyAmount(request.getCurrency_amount())
				.build();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("invalid AERIM callback ext", exception);
		}
	}

	private void validateAmount(BigDecimal amount, ActionTypeEnum actionType) {
		if (amount == null) {
			return;
		}
		if (actionType != ActionTypeEnum.PAYMENT || amount.signum() < 0 || amount.precision() > 20
				|| Math.max(amount.scale(), 0) > 6) {
			throw new IllegalArgumentException("invalid AERIM currency_amount");
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
