package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.delivery.mi.MiDownstreamDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.index.OcpxMediaIndex;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
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
 * 媒体 {@code adapterCode} 为 {@code MI}（忽略大小写）时走小米协议回传，其余（含索引缺失）走现有通用 回调地址回传；不做注册表抽象。
 */
@Component
public class DownstreamDeliveryDispatcher {

	/** 固定线程数 500、队列容量 100 的共享回传线程池。 */
	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static CallbackUrlDownstreamDeliveryService deliveryService;

	private static MiDownstreamDeliveryService miDeliveryService;

	private static OcpxMediaIndex mediaIndex;

	public DownstreamDeliveryDispatcher(CallbackUrlDownstreamDeliveryService deliveryService,
			MiDownstreamDeliveryService miDeliveryService, OcpxMediaIndex mediaIndex) {
		DownstreamDeliveryDispatcher.deliveryService = deliveryService;
		DownstreamDeliveryDispatcher.miDeliveryService = miDeliveryService;
		DownstreamDeliveryDispatcher.mediaIndex = mediaIndex;
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

	/** 按媒体 Adapter 选择回传实现；索引缺失或 Adapter 非 {@code MI} 时回退通用实现。 */
	static DownstreamDeliveryService resolve(CallbackReq request) {
		if (mediaIndex != null) {
			OcpxMedia media = mediaIndex.get(request.getFeedback().getMId());
			if (media != null && "MI".equalsIgnoreCase(media.getAdapterCode())) {
				return miDeliveryService;
			}
		}
		return deliveryService;
	}

}
