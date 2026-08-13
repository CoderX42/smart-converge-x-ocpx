package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.feedback.delivery.kimi.KimiUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UpstreamDeliveryDispatcherTest {

	private final OcpxAdvertiserIndex advertiserIndex = new OcpxAdvertiserIndex();

	private final TemplateGetUpstreamDeliveryService templateDelivery = mock(TemplateGetUpstreamDeliveryService.class);

	private final KimiUpstreamDeliveryService kimiDelivery = mock(KimiUpstreamDeliveryService.class);

	@AfterEach
	void clearIndex() {
		advertiserIndex.clear();
	}

	@Test
	void routesKimiAdvertisersToTheKimiDeliveryIgnoringCase() {
		new UpstreamDeliveryDispatcher(templateDelivery, kimiDelivery, advertiserIndex);
		advertiserIndex.add("kimi-upper", advertiser("KIMI"));
		advertiserIndex.add("kimi-lower", advertiser("kimi"));

		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("kimi-upper"))).isSameAs(kimiDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("kimi-lower"))).isSameAs(kimiDelivery);
	}

	@Test
	void routesOtherOrMissingAdvertisersToTheTemplateDelivery() {
		new UpstreamDeliveryDispatcher(templateDelivery, kimiDelivery, advertiserIndex);
		advertiserIndex.add("template", advertiser("TEMPLATE_GET"));

		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("template"))).isSameAs(templateDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("missing"))).isSameAs(templateDelivery);
	}

	private OcpxAdvertiser advertiser(String adapterCode) {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setAdapterCode(adapterCode);
		return advertiser;
	}

	private FeedbackReq feedback(String advertiserId) {
		return FeedbackReq.builder().aId(advertiserId).build();
	}

}
