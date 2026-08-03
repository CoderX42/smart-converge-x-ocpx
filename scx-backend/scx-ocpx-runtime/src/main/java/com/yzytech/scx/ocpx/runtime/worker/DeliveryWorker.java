package com.yzytech.scx.ocpx.runtime.worker;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.delivery.DeliveryService;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryWorker {

	private final OcpxPersistenceRepository repository;

	private final DeliveryService deliveryService;

	private final TaskExecutor executor;

	private final OcpxRuntimeProperties properties;

	public DeliveryWorker(OcpxPersistenceRepository repository, DeliveryService deliveryService,
			@Qualifier("ocpxDeliveryExecutor") TaskExecutor executor, OcpxRuntimeProperties properties) {
		this.repository = repository;
		this.deliveryService = deliveryService;
		this.executor = executor;
		this.properties = properties;
	}

	@Scheduled(fixedDelay = 50)
	public void dispatch() {
		if (properties.isUpstreamDeliveryEnabled()) {
			repository.claimDeliveryJobs(100, "FEEDBACK_UPSTREAM")
				.forEach(job -> executor.execute(() -> deliveryService.deliver(job)));
		}
		if (properties.isDownstreamDeliveryEnabled()) {
			repository.claimDeliveryJobs(100, "CALLBACK_DOWNSTREAM")
				.forEach(job -> executor.execute(() -> deliveryService.deliver(job)));
		}
	}

}
