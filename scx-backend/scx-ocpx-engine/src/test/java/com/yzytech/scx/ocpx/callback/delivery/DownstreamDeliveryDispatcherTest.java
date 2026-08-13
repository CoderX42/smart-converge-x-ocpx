package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.delivery.mi.MiDownstreamDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.registry.MediaProtocolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownstreamDeliveryDispatcherTest {

	private final CallbackUrlDownstreamDeliveryService genericDelivery = mock(CallbackUrlDownstreamDeliveryService.class);

	private final MiDownstreamDeliveryService miDelivery = mock(MiDownstreamDeliveryService.class);

	private final MediaProtocolRegistry registry = mock(MediaProtocolRegistry.class);

	@Test
	void routesRegisteredMiAndFallsBackToGenericForEveryOtherMedia() {
		new DownstreamDeliveryDispatcher(genericDelivery, miDelivery, registry);
		when(registry.resolve("mi-id")).thenReturn(Optional.of(MediaProtocolRegistry.Protocol.MI));
		when(registry.resolve("generic-id")).thenReturn(Optional.empty());

		assertThat(DownstreamDeliveryDispatcher.resolve(request("mi-id"))).isSameAs(miDelivery);
		assertThat(DownstreamDeliveryDispatcher.resolve(request("generic-id"))).isSameAs(genericDelivery);
	}

	private CallbackReq request(String mediaId) {
		return CallbackReq.builder().feedback(FeedbackReq.builder().mId(mediaId).build()).build();
	}

}
