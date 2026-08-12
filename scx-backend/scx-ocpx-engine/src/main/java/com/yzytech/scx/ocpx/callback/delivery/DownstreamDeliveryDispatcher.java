package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 下游回传异步调度器。
 *
 * <p>
 * 线程池参数和 {@link ThreadPoolExecutor.CallerRunsPolicy} 属于历史兼容约束。队列满时由调用线程
 * 执行任务，以反压方式避免直接丢弃回传。
 */
@Component
public class DownstreamDeliveryDispatcher {

	/** 固定线程数 500、队列容量 100 的共享回传线程池。 */
	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static DownstreamDeliveryService deliveryService;

	public DownstreamDeliveryDispatcher(DownstreamDeliveryService deliveryService) {
		DownstreamDeliveryDispatcher.deliveryService = deliveryService;
	}

	/**
	 * 异步派发通用媒体回传。
	 *
	 * <p>
	 * 为保持旧链路行为，任务内部异常不会继续抛给 Callback 入站请求。
	 * @param request 待回传的转化请求
	 */
	public static void dispatch(CallbackReq request) {
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
