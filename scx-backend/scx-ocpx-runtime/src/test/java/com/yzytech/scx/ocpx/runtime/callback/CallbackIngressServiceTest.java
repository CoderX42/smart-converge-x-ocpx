package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.deduction.DeductionService;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CallbackIngressServiceTest {

	private final CallbackIngressService service = new CallbackIngressService(mock(CallbackTokenService.class),
			mock(OcpxPersistenceRepository.class), mock(DeductionService.class), mock(ReactiveBlockingExecutor.class));

	@Test
	void mapsRawUpstreamCodeThroughTheClickTimePublishedSnapshot() {
		RouteEventSnapshot mapping = new RouteEventSnapshot("mapping-1", "PARTNER_REGISTER", "合作方注册", "REGISTER", "注册",
				"MEDIA_SIGN_UP", "媒体注册", new BigDecimal("3.250000"), 20);
		NormalizedCallbackEvent raw = event(OcpxEventType.UNKNOWN).withSourceEventCode("partner_register");
		CallbackContext context = context(versionedRoute(List.of(mapping)));

		NormalizedCallbackEvent mapped = ReflectionTestUtils.invokeMethod(service, "mapEvent", raw, context);

		assertThat(mapped.eventType()).isEqualTo(OcpxEventType.REGISTER);
		assertThat(mapped.sourceEventCode()).isEqualTo("partner_register");
		assertThat(mapped.mediaEventCode()).isEqualTo("MEDIA_SIGN_UP");
		assertThat(mapped.assessmentUnitPrice()).isEqualByComparingTo("3.250000");
		assertThat(mapped.dailyLimit()).isEqualTo(20);
	}

	@Test
	void rejectsUnmappedEventForANewPublishedRouteVersion() {
		CallbackContext context = context(versionedRoute(List.of()));

		assertThatThrownBy(
				() -> ReflectionTestUtils.invokeMethod(service, "mapEvent", event(OcpxEventType.ACTIVATE), context))
			.isInstanceOfSatisfying(OcpxException.class,
					error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	@Test
	void acceptsOnlyExplicitLegacyAdapterWithoutIntroducingCpaSemantics() {
		CallbackContext context = context(legacyRoute().withLegacyEventAdapter());

		NormalizedCallbackEvent mapped = ReflectionTestUtils.invokeMethod(service, "mapEvent",
				event(OcpxEventType.ACTIVATE), context);

		assertThat(mapped.mediaEventCode()).isEqualTo("ACTIVATE");
		assertThat(mapped.assessmentUnitPrice()).isZero();
		assertThat(context.route().eventSnapshotVersion()).isZero();
		assertThat(context.route().eventMappingMode()).isEqualTo("LEGACY_ADAPTER");
	}

	@Test
	void isolatesLegacyRouteWithoutAnExplicitAdapterInsteadOfGuessingEventType() {
		CallbackContext context = context(legacyRoute());

		assertThatThrownBy(
				() -> ReflectionTestUtils.invokeMethod(service, "mapEvent", event(OcpxEventType.ACTIVATE), context))
			.isInstanceOfSatisfying(OcpxException.class,
					error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	private NormalizedCallbackEvent event(OcpxEventType type) {
		return new NormalizedCallbackEvent("event-1", "click-1", "callback-ref", type,
				OffsetDateTime.parse("2026-08-09T12:00:00+08:00"), null, null);
	}

	private CallbackContext context(RouteSnapshot route) {
		return new CallbackContext("callback-ref", "click-1", route, new FeedbackReqV1());
	}

	private RouteSnapshot legacyRoute() {
		return new RouteSnapshot("monitor-1", "biz-1", 1, "channel-1", "channel", "STANDARD", "HMAC", 1,
				"channel-secret", "advertiser-1", "app-1", "TEMPLATE_GET", null, "https://upstream.example/feedback",
				null, "https://downstream.example/callback", BigDecimal.ONE, BigDecimal.ONE, "OFF", "CPC");
	}

	private RouteSnapshot versionedRoute(List<RouteEventSnapshot> mappings) {
		RouteSnapshot route = legacyRoute();
		return new RouteSnapshot(route.routeId(), route.bizId(), route.routeVersion(), route.channelId(),
				route.channelCode(), route.channelAdapterCode(), route.channelAuthType(), route.channelKeyVersion(),
				route.channelSecret(), route.advertiserId(), route.appLinkId(), route.upstreamAdapterCode(),
				route.upstreamSecret(), route.bidUrl(), route.upstreamRequiredMacros(), route.downstreamCallbackUrl(),
				route.unitPrice(), route.deductionRatio(), route.rtaMode(), route.billingMode(),
				route.clickDedupWindowSeconds(), mappings);
	}

}
