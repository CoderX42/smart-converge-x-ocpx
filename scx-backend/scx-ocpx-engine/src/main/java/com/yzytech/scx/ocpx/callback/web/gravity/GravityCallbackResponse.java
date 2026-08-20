package com.yzytech.scx.ocpx.callback.web.gravity;

/** 引力要求的固定 Callback JSON 响应。 */
public record GravityCallbackResponse(int code, String result) {

	public static GravityCallbackResponse success() {
		return new GravityCallbackResponse(200, "success");
	}

	public static GravityCallbackResponse failed() {
		return new GravityCallbackResponse(400, "failed");
	}

}
