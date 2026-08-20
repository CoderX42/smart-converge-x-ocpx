package com.yzytech.scx.ocpx.callback.web.gravity;

/** 引力 Callback 使用的最小认证上下文；不携带设备标识。 */
public record GravityCallbackContext(int version, String actionType, String mId, String tId, String aId, String lId,
		String requestId, String callbackUrl) {
}
