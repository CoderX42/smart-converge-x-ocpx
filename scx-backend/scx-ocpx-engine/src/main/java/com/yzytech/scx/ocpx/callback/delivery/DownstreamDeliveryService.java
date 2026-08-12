package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 下游媒体回传服务的抽象基类。
 *
 * <p>
 * 实现类负责把已通过扣量判定的转化事件转换成媒体可接受的协议并发出请求。
 */
public abstract class DownstreamDeliveryService {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * 向下游媒体回传一次转化事件。
	 * @param request 已恢复原始 Feedback 上下文的 Callback 请求
	 */
	public abstract void deliver(CallbackReq request);

}
