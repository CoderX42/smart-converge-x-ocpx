package com.yzytech.scx.ocpx.callback.delivery;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DownstreamDeliveryDispatcherTest {

	private final CallbackUrlDownstreamDeliveryService genericDelivery = mock(
			CallbackUrlDownstreamDeliveryService.class);

	@Test
	void alwaysUsesTheGenericCallbackUrlDelivery() {
		new DownstreamDeliveryDispatcher(genericDelivery);

		assertThat(DownstreamDeliveryDispatcher.resolve()).isSameAs(genericDelivery);
	}

	@Test
	void appliesBoundedExecutorSettings() {
		new DownstreamDeliveryDispatcher(genericDelivery, 2, 4, 7);

		ThreadPoolExecutor executor = (ThreadPoolExecutor) DownstreamDeliveryDispatcher.EXECUTORS;
		assertThat(executor.getCorePoolSize()).isEqualTo(2);
		assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
		assertThat(executor.getQueue().remainingCapacity()).isEqualTo(7);
	}

}
