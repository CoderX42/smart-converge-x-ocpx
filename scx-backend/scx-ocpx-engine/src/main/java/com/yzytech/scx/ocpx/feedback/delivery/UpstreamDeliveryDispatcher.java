package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 上游 Feedback 异步调度器。
 *
 * <p>
 * 线程池参数属于历史协议链路的兼容约束。队列满时使用调用方线程执行，避免默默丢弃请求，但会增加 入站请求延迟。
 */
@Component
public class UpstreamDeliveryDispatcher {

	/** 固定线程数 500、队列容量 100 的共享上游请求线程池。 */
	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static UpstreamDeliveryService deliveryService;

	public UpstreamDeliveryDispatcher(UpstreamDeliveryService deliveryService) {
		UpstreamDeliveryDispatcher.deliveryService = deliveryService;
	}

	/**
	 * 异步派发通用 Feedback。
	 *
	 * <p>
	 * 为保持旧链路行为，任务内部异常不会继续抛给 HTTP 入站请求。
	 * @param request 待转发的标准 Feedback
	 */
	public static void dispatch(FeedbackReq request) {
		EXECUTORS.execute(() -> {
			try {
				if (deliveryService != null) {
					deliveryService.deliver(request);
				}
			}
			catch (Exception ignored) {
			}
		});
	}

}
