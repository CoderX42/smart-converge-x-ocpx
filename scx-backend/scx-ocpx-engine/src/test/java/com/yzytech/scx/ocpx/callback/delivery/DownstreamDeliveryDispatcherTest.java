package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.delivery.mi.MiDownstreamDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.index.OcpxMediaIndex;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DownstreamDeliveryDispatcherTest {

	private final OcpxMediaIndex mediaIndex = new OcpxMediaIndex();

	private final CallbackUrlDownstreamDeliveryService genericDelivery = mock(CallbackUrlDownstreamDeliveryService.class);

	private final MiDownstreamDeliveryService miDelivery = mock(MiDownstreamDeliveryService.class);

	@AfterEach
	void clearIndex() {
		mediaIndex.clear();
	}

	@Test
	void routesMiMediaToTheMiDeliveryIgnoringCase() {
		new DownstreamDeliveryDispatcher(genericDelivery, miDelivery, mediaIndex);
		mediaIndex.add("mi-upper", media("MI"));
		mediaIndex.add("mi-lower", media("mi"));

		assertThat(DownstreamDeliveryDispatcher.resolve(request("mi-upper"))).isSameAs(miDelivery);
		assertThat(DownstreamDeliveryDispatcher.resolve(request("mi-lower"))).isSameAs(miDelivery);
	}

	@Test
	void routesOtherOrMissingMediaToTheGenericDelivery() {
		new DownstreamDeliveryDispatcher(genericDelivery, miDelivery, mediaIndex);
		mediaIndex.add("standard", media("STANDARD"));

		assertThat(DownstreamDeliveryDispatcher.resolve(request("standard"))).isSameAs(genericDelivery);
		assertThat(DownstreamDeliveryDispatcher.resolve(request("missing"))).isSameAs(genericDelivery);
	}

	private OcpxMedia media(String adapterCode) {
		OcpxMedia media = new OcpxMedia();
		media.setAdapterCode(adapterCode);
		return media;
	}

	private CallbackReq request(String mediaId) {
		return CallbackReq.builder().feedback(FeedbackReq.builder().mId(mediaId).build()).build();
	}

}
