package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.feedback.delivery.kimi.KimiUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.registry.AdvertiserProtocolRegistry;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpstreamDeliveryDispatcherTest {

	private final TemplateGetUpstreamDeliveryService templateDelivery = mock(TemplateGetUpstreamDeliveryService.class);

	private final KimiUpstreamDeliveryService kimiDelivery = mock(KimiUpstreamDeliveryService.class);

	private final AdvertiserProtocolRegistry registry = mock(AdvertiserProtocolRegistry.class);

	@Test
	void routesOnlyExplicitlyRegisteredAdvertisers() {
		new UpstreamDeliveryDispatcher(templateDelivery, kimiDelivery, registry, mock(OcpxFlowLogWriter.class));
		when(registry.resolve("kimi-id")).thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.KIMI));
		when(registry.resolve("template-id"))
			.thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.TEMPLATE_GET));
		when(registry.resolve("missing-id")).thenReturn(Optional.empty());

		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("kimi-id"))).isSameAs(kimiDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("template-id"))).isSameAs(templateDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("missing-id"))).isNull();
	}

	private FeedbackReq feedback(String advertiserId) {
		return FeedbackReq.builder().aId(advertiserId).build();
	}

}
