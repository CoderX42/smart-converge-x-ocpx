package com.yzytech.scx.ocpx.runtime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

class CocoUpstreamAdapterTest {

	private final CocoUpstreamAdapter adapter = new CocoUpstreamAdapter();

	@Test
	void replacesAndEncodesCocoMacros() {
		FeedbackReqV1 feedback = new FeedbackReqV1();
		feedback.setEvent("click");
		feedback.setOaid("oaid-value");
		feedback.setUa("Mozilla/5.0 test");
		feedback.setOs("android");
		feedback.setModel("Pixel 9");
		feedback.setClickid("click-1");
		feedback.setCreativeId("creative-1");
		RouteSnapshot route = route(
				"https://coco.example/click?oaid=__OAID__&ua=__UA__&os=__OS__&model=__MODEL__&cb=__CALLBACK__&click=__CLICKID__&creative=__CREATIVEID__");

		AdapterHttpRequest request = adapter.buildFeedback(route, feedback,
				"https://rtb.example/rtb/open-api/ocpx/v1/upstreams/coco/callback?ext=signed token");

		assertThat(request.method()).isEqualTo("GET");
		assertThat(request.uri().toString()).contains("oaid=oaid-value", "ua=Mozilla/5.0%20test", "os=android",
				"model=Pixel%209", "click=click-1", "creative=creative-1");
		assertThat(request.uri().toString())
			.contains("cb=https://rtb.example/rtb/open-api/ocpx/v1/upstreams/coco/callback");
		assertThat(request.uri().toString()).doesNotContain("__");
	}

	@Test
	void rejectsUnresolvedRequiredMacro() {
		assertThatThrownBy(() -> adapter.buildFeedback(route("https://coco.example?missing=__UNKNOWN__", null),
				new FeedbackReqV1(), "https://rtb.example/callback"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unresolved");
		assertThatThrownBy(() -> adapter.buildFeedback(route("https://coco.example?oaid=__OAID__", "__OAID__"),
				new FeedbackReqV1(), "https://rtb.example/callback"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unresolved");
	}

	@Test
	void mapsCallbackAndKeepsMalformedAmountForAudit() {
		NormalizedCallbackEvent payment = adapter.parseCallback("ref-1",
				Map.of("action_type", "purchase", "currency_amount", "12.34"));
		assertThat(payment.eventType()).isEqualTo(OcpxEventType.PAYMENT);
		assertThat(payment.amount()).isEqualByComparingTo(new BigDecimal("12.34"));
		assertThat(payment.amountRaw()).isEqualTo("12.34");

		NormalizedCallbackEvent malformed = adapter.parseCallback("ref-1",
				Map.of("action_type", "register", "currency_amount", "unknown"));
		assertThat(malformed.amount()).isNull();
		assertThat(malformed.amountRaw()).isEqualTo("unknown");
		NormalizedCallbackEvent negative = adapter.parseCallback("ref-1",
				Map.of("action_type", "payment", "currency_amount", "-1.00"));
		assertThat(negative.amount()).isNull();
		assertThat(negative.amountRaw()).isEqualTo("-1.00");

		NormalizedCallbackEvent anotherOrder = adapter.parseCallback("ref-1",
				Map.of("action_type", "purchase", "currency_amount", "12.34", "order_id", "order-2"));
		assertThat(anotherOrder.eventId()).isNotEqualTo(payment.eventId());
	}

	private RouteSnapshot route(String bidUrl) {
		return route(bidUrl, null);
	}

	private RouteSnapshot route(String bidUrl, String requiredMacros) {
		return new RouteSnapshot("route-1", "biz-1", 3, "channel-1", "demo", "STANDARD", "HMAC", 1, "secret",
				"advertiser-1", "app-1", "COCO", null, bidUrl, requiredMacros, "https://downstream.example/callback",
				BigDecimal.ONE, BigDecimal.ONE, "OFF", "OFF");
	}

}
