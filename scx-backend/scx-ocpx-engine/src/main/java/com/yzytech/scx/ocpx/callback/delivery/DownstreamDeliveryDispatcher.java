package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.delivery.mi.MiDownstreamDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.protocol.registry.MediaProtocolRegistry;
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
 * 媒体主键登记为 MI 时走小米协议；其余媒体按参考实现走 Feedback 自带回调地址。
 */
@Component
public class DownstreamDeliveryDispatcher {

	/** 固定线程数 500、队列容量 100 的共享回传线程池。 */
	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static CallbackUrlDownstreamDeliveryService deliveryService;

	private static MiDownstreamDeliveryService miDeliveryService;

	private static MediaProtocolRegistry protocolRegistry;

	public DownstreamDeliveryDispatcher(CallbackUrlDownstreamDeliveryService deliveryService,
			MiDownstreamDeliveryService miDeliveryService, MediaProtocolRegistry protocolRegistry) {
		DownstreamDeliveryDispatcher.deliveryService = deliveryService;
		DownstreamDeliveryDispatcher.miDeliveryService = miDeliveryService;
		DownstreamDeliveryDispatcher.protocolRegistry = protocolRegistry;
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
				DownstreamDeliveryService service = resolve(request);
				if (service != null) {
					service.deliver(request);
				}
			}
			catch (Exception ignored) {
			}
		});
	}

	/** 按媒体主键固定注册表选择特殊协议；未登记媒体回退通用实现。 */
	static DownstreamDeliveryService resolve(CallbackReq request) {
		if (protocolRegistry != null && protocolRegistry.resolve(request.getFeedback().getMId())
			.orElse(null) == MediaProtocolRegistry.Protocol.MI) {
			return miDeliveryService;
		}
		return deliveryService;
	}

}
