package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.oppo.callback.OppoUploadActiveDataDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

	/** 有界共享回传线程池；启动时由配置覆盖默认值。 */
	public static volatile ExecutorService EXECUTORS = createExecutor(32, 64, 200);

	private static CallbackUrlDownstreamDeliveryService deliveryService;
	private static OppoUploadActiveDataDeliveryService oppoDeliveryService;

	public DownstreamDeliveryDispatcher(CallbackUrlDownstreamDeliveryService deliveryService) {
		this(deliveryService, 32, 64, 200);
	}

	public DownstreamDeliveryDispatcher(CallbackUrlDownstreamDeliveryService deliveryService, int coreThreads,
			int maxThreads, int queueCapacity) {
		this(deliveryService, null, coreThreads, maxThreads, queueCapacity);
	}

	public DownstreamDeliveryDispatcher(CallbackUrlDownstreamDeliveryService deliveryService,
			OppoUploadActiveDataDeliveryService oppoDeliveryService,
			@Value("${ocpx.delivery.downstream.core-threads:32}") int coreThreads,
			@Value("${ocpx.delivery.downstream.max-threads:64}") int maxThreads,
			@Value("${ocpx.delivery.downstream.queue-capacity:200}") int queueCapacity) {
		DownstreamDeliveryDispatcher.deliveryService = deliveryService;
		DownstreamDeliveryDispatcher.oppoDeliveryService = oppoDeliveryService;
		ExecutorService previous = EXECUTORS;
		EXECUTORS = createExecutor(coreThreads, maxThreads, queueCapacity);
		if (previous != EXECUTORS) {
			previous.shutdown();
		}
	}

	private static ExecutorService createExecutor(int coreThreads, int maxThreads, int queueCapacity) {
		int safeCoreThreads = Math.max(1, coreThreads);
		int safeMaxThreads = Math.max(safeCoreThreads, maxThreads);
		int safeQueueCapacity = Math.max(1, queueCapacity);
		return new ThreadPoolExecutor(safeCoreThreads, safeMaxThreads, 30L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(safeQueueCapacity), new ThreadPoolExecutor.CallerRunsPolicy());
	}

	/**
	 * 异步派发通用媒体回传。
	 *
	 * <p>
	 * 为保持旧链路行为，任务内部异常不会继续抛给 Callback 入站请求。
	 * @param request 待回传的转化请求
	 */
	public static void dispatch(CallbackReq request) {
		if (oppoDeliveryService != null && oppoDeliveryService.supports(request)) {
			EXECUTORS.execute(() -> oppoDeliveryService.deliver(request));
			return;
		}
		// Snapshot before enqueueing so a RTA-protected callback cannot retain or
		// expose raw identifiers through the asynchronous worker.  The snapshot keeps
		// a validated callback destination when it is free of raw device values.
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
