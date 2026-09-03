package com.yzytech.scx.ocpx.callback.web.phoenix;

/** 凤凰新闻 Callback 成功响应，字段和值必须保持厂商约定。 */
public record PhoenixCallbackResponse(int code, String msg, String data) {

	private static final PhoenixCallbackResponse SUCCESS = new PhoenixCallbackResponse(0, "success", "");

	public static PhoenixCallbackResponse success() {
		return SUCCESS;
	}

}
