package com.yzytech.scx.ocpx.callback.web.gravity;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 将引力归因成功 Callback 的认证上下文转换为 SCX 激活事件。 */
@Component
@RequiredArgsConstructor
public class GravityCallbackAdapter {

	private static final int CONTEXT_VERSION = 1;

	private final GravityCallbackContextCodec contextCodec;

	public CallbackReq toCallback(String token, String keyId) {
		return toCallback(contextCodec.decode(token, keyId));
	}

	public CallbackReq toCallback(String token) {
		return toCallback(contextCodec.decodeCompatible(token));
	}

	private CallbackReq toCallback(GravityCallbackContext context) {
		validate(context);
		FeedbackReq feedback = FeedbackReq.builder()
			.mId(context.mId())
			.tId(context.tId())
			.aId(context.aId())
			.lId(context.lId())
			.reqId(context.requestId())
			.callbackUrl(context.callbackUrl())
			.event(EventTypeEnum.CLICK)
			.build();
		return CallbackReq.builder()
			.feedback(feedback)
			.actionType(ActionTypeEnum.ACTIVATE)
			.actionTime(System.currentTimeMillis())
			.partnerActionType("ATTRIBUTION_SUCCESS")
			.build();
	}

	private void validate(GravityCallbackContext context) {
		if (context == null || context.version() != CONTEXT_VERSION
				|| !ActionTypeEnum.ACTIVATE.name().equals(context.actionType())) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
		require(context.mId(), 32);
		require(context.tId(), 32);
		require(context.aId(), 32);
		require(context.lId(), 32);
		require(context.requestId(), 128);
		if (context.callbackUrl() != null && context.callbackUrl().length() > 5000) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
	}

	private void require(String value, int maxLength) {
		if (!StringUtils.hasText(value) || value.length() > maxLength) {
			throw new IllegalArgumentException("GRAVITY_CALLBACK_CONTEXT_INVALID");
		}
	}

}
