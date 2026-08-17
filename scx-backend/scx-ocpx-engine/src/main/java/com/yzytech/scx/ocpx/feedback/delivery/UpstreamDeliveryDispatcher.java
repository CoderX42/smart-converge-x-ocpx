package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.feedback.delivery.aerim.AerimUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.delivery.weifeng.WeifengUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.registry.AdvertiserProtocolRegistry;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import lombok.extern.slf4j.Slf4j;
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
 *
 * <p>
 * 广告主主键必须显式登记为通用模板、AERIM 或 WEIFENG；未登记主键拒绝上游转发并写脱敏流程日志。
 */
@Slf4j
@Component
public class UpstreamDeliveryDispatcher {

	/** 固定线程数 500、队列容量 100 的共享上游请求线程池。 */
	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static TemplateGetUpstreamDeliveryService deliveryService;

	private static AerimUpstreamDeliveryService aerimDeliveryService;

	private static WeifengUpstreamDeliveryService weifengDeliveryService;

	private static AdvertiserProtocolRegistry protocolRegistry;

	private static OcpxFlowLogWriter flowLogWriter;

	public UpstreamDeliveryDispatcher(TemplateGetUpstreamDeliveryService deliveryService,
			AerimUpstreamDeliveryService aerimDeliveryService, WeifengUpstreamDeliveryService weifengDeliveryService,
			AdvertiserProtocolRegistry protocolRegistry, OcpxFlowLogWriter flowLogWriter) {
		UpstreamDeliveryDispatcher.deliveryService = deliveryService;
		UpstreamDeliveryDispatcher.aerimDeliveryService = aerimDeliveryService;
		UpstreamDeliveryDispatcher.weifengDeliveryService = weifengDeliveryService;
		UpstreamDeliveryDispatcher.protocolRegistry = protocolRegistry;
		UpstreamDeliveryDispatcher.flowLogWriter = flowLogWriter;
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
				UpstreamDeliveryService service = resolve(request);
				if (service != null) {
					service.deliver(request);
				}
				else {
					log.warn("Reject unregistered advertiser protocol aId:{}", request.getAId());
					flowLogWriter.rejectedAdvertiser(request);
				}
			}
			catch (Exception error) {
				log.warn("Upstream delivery failed aId:{} lId:{}", request.getAId(), request.getLId());
			}
		});
	}

	/** 按服务端配置的主键注册表选择投递实现；未登记时返回 {@code null}。 */
	static UpstreamDeliveryService resolve(FeedbackReq request) {
		if (protocolRegistry == null) {
			return null;
		}
		return protocolRegistry.resolve(request.getAId()).map(protocol -> switch (protocol) {
			case AERIM -> aerimDeliveryService;
			case WEIFENG -> weifengDeliveryService;
			case TEMPLATE_GET -> deliveryService;
		}).orElse(null);
	}

}
