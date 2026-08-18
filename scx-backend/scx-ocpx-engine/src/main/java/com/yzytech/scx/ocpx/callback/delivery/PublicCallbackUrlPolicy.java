package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.contract.PublicCallbackUrlContract;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 通用媒体回调的出站 SSRF 防护。 */
@Component
public class PublicCallbackUrlPolicy {

	private final PublicCallbackUrlContract contract = new PublicCallbackUrlContract();

	/**
	 * 校验并规范化通用媒体回调地址。
	 * @param value Feedback 携带并已解码的 URL
	 * @return 可用于出站请求的规范 URL
	 */
	public String requirePublicHttpUrl(String value) {
		return contract.requirePublicHttpUrl(value);
	}

	String appendEventType(String callbackUrl, int eventType) {
		String separator = callbackUrl.contains("?") ? "&" : "?";
		return callbackUrl + separator + "event_type=" + eventType;
	}

	String appendCallbackFields(String callbackUrl, int eventType, BigDecimal currencyAmount) {
		return contract.appendCallbackFields(callbackUrl, eventType, currencyAmount);
	}

}
