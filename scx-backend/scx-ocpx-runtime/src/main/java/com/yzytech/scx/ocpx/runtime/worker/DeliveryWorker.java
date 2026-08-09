package com.yzytech.scx.ocpx.runtime.worker;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.delivery.DeliveryService;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DeliveryWorker {

	private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorker.class);

	private final OcpxPersistenceRepository repository;

	private final DeliveryService deliveryService;

	private final OcpxRuntimeProperties properties;

	private final ReactiveBlockingExecutor blockingExecutor;

	private final AtomicBoolean running = new AtomicBoolean();

	public DeliveryWorker(OcpxPersistenceRepository repository, DeliveryService deliveryService,
			OcpxRuntimeProperties properties, ReactiveBlockingExecutor blockingExecutor) {
		this.repository = repository;
		this.deliveryService = deliveryService;
		this.properties = properties;
		this.blockingExecutor = blockingExecutor;
	}

	/**
	 * 定时器只是后台订阅边界。Service 与 Adapter 不主动订阅，因此错误、限流和线程模型都集中在这里可见。
	 */
	@Scheduled(fixedDelay = 50)
	public void dispatch() {
		if ((!properties.isUpstreamDeliveryEnabled() && !properties.isDownstreamDeliveryEnabled())
				|| !running.compareAndSet(false, true)) {
			return;
		}
		Flux.concat(properties.isUpstreamDeliveryEnabled() ? claimAndDeliver("FEEDBACK_UPSTREAM") : Flux.empty(),
				properties.isDownstreamDeliveryEnabled() ? claimAndDeliver("CALLBACK_DOWNSTREAM") : Flux.empty())
			.then()
			.doFinally(ignored -> running.set(false))
			.subscribe(null, error -> LOG.error("OCPX delivery worker failed", error));
	}

	private Flux<Void> claimAndDeliver(String direction) {
		return blockingExecutor.call(() -> repository.claimDeliveryJobs(100, direction))
			.flatMapMany(Flux::fromIterable)
			.flatMap(job -> {
				try (MDC.MDCCloseable ignored = MDC.putCloseable("debugExecutionId",
						job.debugExecutionId() == null ? "-" : job.debugExecutionId())) {
					if (job.debugExecutionId() != null) {
						LOG.info("Resuming correlated API debug delivery job={}", job.id());
					}
					return deliveryService.deliver(job);
				}
			}, properties.getAdapterConcurrency());
	}

}
