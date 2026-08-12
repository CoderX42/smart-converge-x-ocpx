package com.yzytech.scx.ocpx.callback.web;

import lombok.Data;

/** 上游 Callback 的加密上下文与兼容状态参数。 */
@Data
public class CallbackContextRequest {

	/** AES URL-safe Base64 加密的原始 Feedback 上下文。 */
	private String ext;

	/** 兼容旧协议的状态参数，当前由链接 ID 和任务 ID 组成。 */
	private String s;

}
