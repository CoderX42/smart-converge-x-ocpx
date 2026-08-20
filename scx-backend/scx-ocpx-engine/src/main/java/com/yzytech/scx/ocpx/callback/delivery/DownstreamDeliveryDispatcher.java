package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import lombok.extern.slf4j.Slf4j;
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
 *
 * <p>
 * 所有媒体统一使用 Feedback 自带的 Callback URL 回传。
 */
@Component
@Slf4j
public class DownstreamDeliveryDispatcher {

	/** 固定线程数 500、队列容量 100 的共享回传线程池。 */
	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static CallbackUrlDownstreamDeliveryService deliveryService;

	public DownstreamDeliveryDispatcher(CallbackUrlDownstreamDeliveryService deliveryService) {
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
				DownstreamDeliveryService service = resolve();
				if (service != null) {
					service.deliver(request);
				}
			}
			catch (Exception exception) {
				log.warn("Downstream delivery failed type:{}", exception.getClass().getSimpleName());
			}
		});
	}

	/** 返回统一的通用媒体 Callback URL 投递实现。 */
	static DownstreamDeliveryService resolve() {
		return deliveryService;
	}

}
