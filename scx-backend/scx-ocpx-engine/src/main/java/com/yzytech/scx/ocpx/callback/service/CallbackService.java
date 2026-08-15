package com.yzytech.scx.ocpx.callback.service;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;

/** Callback MySQL 去重、记录、小时汇总、扣量和下游回传的业务入口。 */
public interface CallbackService {

	/**
	 * 处理一条上游广告主转化回调。
	 * @param request 已完成协议解析的统一 Callback 请求
	 * @return 去重状态和最终处置结果
	 */
	CallbackHandleResult handleCallback(CallbackReq request);

}
