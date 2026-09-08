package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.feedback.delivery.aerim.AerimUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.delivery.gravity.GravityUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.delivery.renrenvideo.RenrenVideoUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.delivery.weifeng.WeifengUpstreamDeliveryService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.registry.AdvertiserProtocolRegistry;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpstreamDeliveryDispatcherTest {

	private final TemplateGetUpstreamDeliveryService templateDelivery = mock(TemplateGetUpstreamDeliveryService.class);

	private final AerimUpstreamDeliveryService aerimDelivery = mock(AerimUpstreamDeliveryService.class);

	private final WeifengUpstreamDeliveryService weifengDelivery = mock(WeifengUpstreamDeliveryService.class);

	private final GravityUpstreamDeliveryService gravityDelivery = mock(GravityUpstreamDeliveryService.class);

	private final RenrenVideoUpstreamDeliveryService renrenVideoDelivery = mock(
			RenrenVideoUpstreamDeliveryService.class);

	private final AdvertiserProtocolRegistry registry = mock(AdvertiserProtocolRegistry.class);

	@Test
	void routesOnlyExplicitlyRegisteredAdvertisers() {
		new UpstreamDeliveryDispatcher(templateDelivery, aerimDelivery, weifengDelivery, gravityDelivery,
				renrenVideoDelivery, registry, mock(OcpxFlowLogWriter.class));
		when(registry.resolve("template-id")).thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.TEMPLATE_GET));
		when(registry.resolve("aerim-id")).thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.AERIM));
		when(registry.resolve("weifeng-id")).thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.WEIFENG));
		when(registry.resolve("gravity-id")).thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.GRAVITY));
		when(registry.resolve("renren-video-id"))
			.thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.RENREN_VIDEO));
		when(registry.resolve("missing-id")).thenReturn(Optional.empty());

		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("template-id"))).isSameAs(templateDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("aerim-id"))).isSameAs(aerimDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("weifeng-id"))).isSameAs(weifengDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("gravity-id"))).isSameAs(gravityDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("renren-video-id"))).isSameAs(renrenVideoDelivery);
		assertThat(UpstreamDeliveryDispatcher.resolve(feedback("missing-id"))).isNull();
	}

	@Test
	void appliesBoundedExecutorSettings() {
		new UpstreamDeliveryDispatcher(templateDelivery, aerimDelivery, weifengDelivery, gravityDelivery,
				renrenVideoDelivery, registry, mock(OcpxFlowLogWriter.class), 2, 4, 7);

		ThreadPoolExecutor executor = (ThreadPoolExecutor) UpstreamDeliveryDispatcher.EXECUTORS;
		assertThat(executor.getCorePoolSize()).isEqualTo(2);
		assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
		assertThat(executor.getQueue().remainingCapacity()).isEqualTo(7);
	}

	@Test
	void rtaProtectedDispatchUsesRawIdentifierFreeDetachedSnapshot() {
		FeedbackReq original = FeedbackReq.builder()
			.aId("adv-1")
			.tId("task-1")
			.idfa("raw-idfa")
			.imei("raw-imei")
			.oaid("raw-oaid")
			.androidid("raw-android")
			.caid("raw-caid")
			.mac("raw-mac")
			.ext("opaque-raw-payload")
			.idfaMd5("a".repeat(32))
			.imeiMd5("b".repeat(32))
			.rtaProtected(true)
			.build();

		FeedbackReq copy = original.copyForRtaDownstream();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getIdfa()).isNull();
		assertThat(copy.getImei()).isNull();
		assertThat(copy.getOaid()).isNull();
		assertThat(copy.getAndroidid()).isNull();
		assertThat(copy.getCaid()).isNull();
		assertThat(copy.getMac()).isNull();
		assertThat(copy.getExt()).isNull();
		assertThat(copy.getCallbackUrl()).isNull();
		assertThat(copy.getIdfaMd5()).isEqualTo(original.getIdfaMd5());
		assertThat(copy.getImeiMd5()).isEqualTo(original.getImeiMd5());
		assertThat(original.getIdfa()).isEqualTo("raw-idfa");
	}

	@Test
	void dispatcherPassesSanitizedSnapshotToTheAsyncProtocolService() {
		new UpstreamDeliveryDispatcher(templateDelivery, aerimDelivery, weifengDelivery, gravityDelivery,
				renrenVideoDelivery, registry, mock(OcpxFlowLogWriter.class));
		when(registry.resolve("weifeng-id")).thenReturn(Optional.of(AdvertiserProtocolRegistry.Protocol.WEIFENG));
		FeedbackReq original = FeedbackReq.builder()
			.aId("weifeng-id")
			.tId("task-1")
			.idfa("raw-idfa")
			.imei("raw-imei")
			.oaid("raw-oaid")
			.androidid("raw-android")
			.caid("raw-caid")
			.caidList("raw-caid-list")
			.caid1("raw-caid-1")
			.caid2("raw-caid-2")
			.lastCaid("raw-last-caid")
			.prevCaid("raw-prev-caid")
			.aaid("raw-aaid")
			.mac("raw-mac")
			.ext("opaque-raw-payload")
			.callbackUrl("https://example.test/?idfa=raw-idfa")
			.idfaMd5("a".repeat(32))
			.rtaProtected(true)
			.build();

		UpstreamDeliveryDispatcher.dispatch(original);
		ArgumentCaptor<FeedbackReq> captured = ArgumentCaptor.forClass(FeedbackReq.class);
		org.mockito.Mockito.verify(weifengDelivery, org.mockito.Mockito.timeout(1000)).deliver(captured.capture());

		FeedbackReq outbound = captured.getValue();
		assertThat(outbound).isNotSameAs(original);
		assertThat(outbound.getIdfa()).isNull();
		assertThat(outbound.getImei()).isNull();
		assertThat(outbound.getOaid()).isNull();
		assertThat(outbound.getAndroidid()).isNull();
		assertThat(outbound.getCaid()).isNull();
		assertThat(outbound.getCaidList()).isNull();
		assertThat(outbound.getCaid1()).isNull();
		assertThat(outbound.getCaid2()).isNull();
		assertThat(outbound.getLastCaid()).isNull();
		assertThat(outbound.getPrevCaid()).isNull();
		assertThat(outbound.getAaid()).isNull();
		assertThat(outbound.getMac()).isNull();
		assertThat(outbound.getExt()).isNull();
		assertThat(outbound.getCallbackUrl()).isNull();
		assertThat(outbound.getIdfaMd5()).isEqualTo("a".repeat(32));
	}

	private FeedbackReq feedback(String advertiserId) {
		return FeedbackReq.builder().aId(advertiserId).build();
	}

}
