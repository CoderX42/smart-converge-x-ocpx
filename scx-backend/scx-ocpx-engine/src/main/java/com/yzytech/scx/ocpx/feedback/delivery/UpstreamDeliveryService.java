package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上游广告主请求服务的抽象基类。
 *
 * <p>
 * 实现类负责把标准 Feedback 转换为具体广告主协议；当前通用实现只转发点击事件。
 */
public abstract class UpstreamDeliveryService {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final Logger errorLog = LoggerFactory.getLogger("error");

	/**
	 * 向上游广告主发送一次 Feedback。
	 * @param request 已标准化的媒体事件
	 */
	public abstract void deliver(FeedbackReq request);

}
