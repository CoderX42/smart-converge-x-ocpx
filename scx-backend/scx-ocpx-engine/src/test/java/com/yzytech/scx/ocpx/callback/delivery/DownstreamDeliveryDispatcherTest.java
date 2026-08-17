package com.yzytech.scx.ocpx.callback.delivery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DownstreamDeliveryDispatcherTest {

	private final CallbackUrlDownstreamDeliveryService genericDelivery = mock(CallbackUrlDownstreamDeliveryService.class);

	@Test
	void alwaysUsesTheGenericCallbackUrlDelivery() {
		new DownstreamDeliveryDispatcher(genericDelivery);

		assertThat(DownstreamDeliveryDispatcher.resolve()).isSameAs(genericDelivery);
	}

}
