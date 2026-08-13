package com.yzytech.scx.ocpx.callback.web.kimi;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** KIMI 广告主 Callback 参数适配器（SGYZ KimiParamAdapter 入站半平移）。 */
@Component
public class KimiCallbackAdapter {

	@Value("${app.callback-aes-key}")
	private String callbackAesKey;

	/**
	 * 解密 {@code ext} 还原原始 Feedback，并映射 KIMI 动作类型。
	 * @param request query 与 body 合并后的 KIMI 转化参数
	 * @return 包含原 Feedback、动作类型和动作时间的统一 Callback
	 * @throws NullPointerException ext 无法解密时抛出，由 Controller 转换为 HTTP 500
	 */
	public CallbackReq toCallback(KimiCallbackReq request) {
		String ext = Objects.requireNonNull(AesUtil.decryptBase64(request.getExt(), callbackAesKey));
		FeedbackReq feedback = JSON.parseObject(ext, FeedbackReq.class);
		return CallbackReq.builder()
			.feedback(feedback)
			.actionType(matchAction(request))
			.actionTime(request.getAction_time())
			.build();
	}

	private ActionTypeEnum matchAction(KimiCallbackReq request) {
		// @formatter:off
		return switch (request.getAction_type()) {
			case "activation" -> ActionTypeEnum.ACTIVATE;
			case "retention_2" -> ActionTypeEnum.NEXT_STAY;
			default -> null;
		};
		// @formatter:on
	}

}
