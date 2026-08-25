package com.yzytech.scx.ocpx.callback.web.weifeng;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/** 微风互动 v1.3 字符串事件与 AES Feedback 上下文适配器。 */
@Component
public class WeifengCallbackAdapter {

	private static final Map<String, ActionTypeEnum> ACTIONS = Map.ofEntries(
			Map.entry("ACTIVATED", ActionTypeEnum.ACTIVATE), Map.entry("REGISTERED", ActionTypeEnum.REGISTER),
			Map.entry("PAID", ActionTypeEnum.PAYMENT), Map.entry("RETAINED", ActionTypeEnum.NEXT_STAY),
			Map.entry("FIRST_DAY_PAID", ActionTypeEnum.FIRST_ORDER), Map.entry("APP_OPEN", ActionTypeEnum.EVOKE));

	@Value("${app.callback-crypto.keys.v1}")
	private String callbackAesKey;

	@Autowired(required = false)
	private OcpxKeyRingCrypto callbackCrypto;

	public CallbackReq toCallback(WeifengCallbackRequest request) {
		if (request == null || !StringUtils.hasText(request.getExt()) || !StringUtils.hasText(request.getS())
				|| !StringUtils.hasText(request.getEvent())) {
			throw new IllegalArgumentException("missing WEIFENG callback parameter");
		}
		String partnerEvent = request.getEvent().trim().toUpperCase(Locale.ROOT);
		ActionTypeEnum actionType = ACTIONS.get(partnerEvent);
		if (actionType == null) {
			throw new IllegalArgumentException("unknown WEIFENG event");
		}
		BigDecimal amount = parseAmount(request.getAmount(), actionType);
		validateDays(request.getDays(), actionType);
		String clearText = callbackCrypto == null ? AesUtil.decryptBase64(request.getExt(), callbackAesKey)
				: callbackCrypto.decryptCallback(request.getKid(), request.getExt());
		if (!StringUtils.hasText(clearText)) {
			throw new IllegalArgumentException("invalid WEIFENG callback ext");
		}
		try {
			FeedbackReq feedback = JSON.parseObject(clearText, FeedbackReq.class);
			requireRouteIds(feedback);
			return CallbackReq.builder()
				.feedback(feedback)
				.actionType(actionType)
				.actionTime(System.currentTimeMillis())
				.partnerActionType(partnerEvent)
				.currencyAmount(amount)
				.build();
		}
		catch (IllegalArgumentException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("invalid WEIFENG callback ext", exception);
		}
	}

	private BigDecimal parseAmount(String value, ActionTypeEnum actionType) {
		if (!StringUtils.hasText(value) || "__AMOUNT__".equals(value)) {
			return null;
		}
		if (actionType != ActionTypeEnum.PAYMENT && actionType != ActionTypeEnum.FIRST_ORDER) {
			throw new IllegalArgumentException("invalid WEIFENG amount event");
		}
		try {
			BigDecimal amount = new BigDecimal(value);
			if (amount.signum() < 0 || amount.precision() > 20 || Math.max(amount.scale(), 0) > 6) {
				throw new IllegalArgumentException("invalid WEIFENG amount");
			}
			return amount;
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException("invalid WEIFENG amount", exception);
		}
	}

	private void validateDays(String value, ActionTypeEnum actionType) {
		if (!StringUtils.hasText(value) || "__DAYS__".equals(value)) {
			return;
		}
		if (actionType != ActionTypeEnum.NEXT_STAY) {
			throw new IllegalArgumentException("invalid WEIFENG days event");
		}
		try {
			int days = Integer.parseInt(value);
			if (days < 0 || days > 3650) {
				throw new IllegalArgumentException("invalid WEIFENG days");
			}
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException("invalid WEIFENG days", exception);
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
