package com.yzytech.scx.ocpx.runtime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
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

		AdapterHttpRequest request = adapter
			.buildFeedback(route, feedback,
					"https://ocpx.example/ocpx/open-api/ocpx/v1/upstreams/coco/callback?ext=signed token")
			.block();

		assertThat(request.method()).isEqualTo("GET");
		assertThat(request.uri().toString()).contains("oaid=oaid-value", "ua=Mozilla/5.0%20test", "os=android",
				"model=Pixel%209", "click=click-1", "creative=creative-1");
		assertThat(request.uri().toString())
			.contains("cb=https://ocpx.example/ocpx/open-api/ocpx/v1/upstreams/coco/callback");
		assertThat(request.uri().toString()).doesNotContain("__");
	}

	@Test
	void rejectsUnresolvedRequiredMacro() {
		assertThatThrownBy(() -> adapter
			.buildFeedback(route("https://coco.example?missing=__UNKNOWN__", null), new FeedbackReqV1(),
					"https://ocpx.example/callback")
			.block()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unresolved");
		assertThatThrownBy(() -> adapter
			.buildFeedback(route("https://coco.example?oaid=__OAID__", "__OAID__"), new FeedbackReqV1(),
					"https://ocpx.example/callback")
			.block()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unresolved");
	}

	@Test
	void mapsCallbackAndKeepsMalformedAmountForAudit() {
		NormalizedCallbackEvent payment = adapter
			.parseCallback("ref-1", Map.of("action_type", "purchase", "currency_amount", "12.34"))
			.block();
		assertThat(payment.eventType()).isEqualTo(OcpxEventType.PAYMENT);
		assertThat(payment.amount()).isEqualByComparingTo(new BigDecimal("12.34"));
		assertThat(payment.amountRaw()).isEqualTo("12.34");

		NormalizedCallbackEvent malformed = adapter
			.parseCallback("ref-1", Map.of("action_type", "purchase", "currency_amount", "unknown"))
			.block();
		assertThat(malformed.amount()).isNull();
		assertThat(malformed.amountRaw()).isEqualTo("unknown");
		NormalizedCallbackEvent negative = adapter
			.parseCallback("ref-1", Map.of("action_type", "payment", "currency_amount", "-1.00"))
			.block();
		assertThat(negative.amount()).isNull();
		assertThat(negative.amountRaw()).isEqualTo("-1.00");

		NormalizedCallbackEvent anotherOrder = adapter
			.parseCallback("ref-1",
					Map.of("action_type", "purchase", "currency_amount", "12.34", "order_id", "order-2"))
			.block();
		assertThat(anotherOrder.eventId()).isNotEqualTo(payment.eventId());
	}

	@Test
	void supportsOfficialMacroAliasesAndSingleQueryEncoding() {
		FeedbackReqV1 feedback = new FeedbackReqV1();
		feedback.setEvent("click");
		feedback.setOaidMd5("oaid-md5");
		feedback.setAndroidId("android id");
		feedback.setAndroidIdMd5("android-md5");
		feedback.setCaid("[{\"version\":\"20240716\"}]");
		feedback.setCampaignId("campaign-1");
		feedback.setAdgroupId("adgroup-1");
		feedback.setAdvertiserId("advertiser-1");
		RouteSnapshot route = route("https://coco.example/click?a=__ANDROIDID__&b=__ANDROID_ID__"
				+ "&am=__ANDROIDID_MD5__&bm=__ANDROID_ID_MD5__&caid=__CAID__&caids=__CAID_LIST__"
				+ "&campaign=__CAMPAIGNID__&adgroup=__ADGROUPID__&advertiser=__ADVERTISERID__&cb=__CALLBACK__");

		adapter.validateFeedback(route, feedback).block();
		String url = adapter.buildFeedback(route, feedback, "https://ocpx.example/callback?ext=signed token")
			.block()
			.uri()
			.toString();

		assertThat(url).contains("a=android%20id", "b=android%20id", "campaign=campaign-1", "adgroup=adgroup-1",
				"advertiser=advertiser-1");
		assertThat(url).doesNotContain("%2520", "__");
	}

	@Test
	void validatesClickAndroidIdentifiersAndRequiredMacros() {
		FeedbackReqV1 feedback = new FeedbackReqV1();
		feedback.setEvent("activate");
		assertThatThrownBy(
				() -> adapter.validateFeedback(route("https://coco.example?cb=__CALLBACK__"), feedback).block())
			.hasMessageContaining("CLICK");

		feedback.setEvent("click");
		feedback.setOs("android");
		assertThatThrownBy(
				() -> adapter.validateFeedback(route("https://coco.example?cb=__CALLBACK__"), feedback).block())
			.hasMessageContaining("IMEI/OAID");

		feedback.setOaidMd5("oaid-md5");
		assertThatThrownBy(() -> adapter
			.validateFeedback(route("https://coco.example?cb=__CALLBACK__&campaign=__CAMPAIGNID__", "__CAMPAIGNID__"),
					feedback)
			.block()).hasMessageContaining("__CAMPAIGNID__");
	}

	@Test
	void appliesCocoBusinessSuccessCodes() {
		assertThat(adapter.evaluateFeedbackResponse(response(200, "")).block().success()).isTrue();
		assertThat(adapter.evaluateFeedbackResponse(response(204, "")).block().success()).isTrue();
		assertThat(adapter.evaluateFeedbackResponse(response(200, "{}")).block().success()).isTrue();
		assertThat(adapter.evaluateFeedbackResponse(response(200, "{\"code\":0}")).block().success()).isTrue();
		assertThat(adapter.evaluateFeedbackResponse(response(200, "{\"code\":200}")).block().success()).isTrue();
		assertThat(adapter.evaluateFeedbackResponse(response(200, "{\"code\":402,\"message\":\"quota\"}"))
			.block()
			.success()).isFalse();
		assertThat(adapter.evaluateFeedbackResponse(response(200, "{\"code\":402,\"message\":\"quota\"}"))
			.block()
			.reason()).isEqualTo("COCO_CODE_402:quota");
		assertThat(adapter.evaluateFeedbackResponse(response(500, "{\"code\":0}")).block().success()).isFalse();
	}

	@Test
	void prioritizesExternalCallbackIdsAndMapsFirstOrder() {
		NormalizedCallbackEvent first = adapter
			.parseCallback("ref-1", Map.of("action_type", "firstorder", "event_id", "event-1", "order_id", "order-1"))
			.block();
		NormalizedCallbackEvent repeated = adapter
			.parseCallback("ref-1",
					Map.of("action_type", "firstorder", "event_id", "event-1", "order_id", "changed-order"))
			.block();

		assertThat(first.eventType()).isEqualTo(OcpxEventType.FIRST_ORDER);
		assertThat(first.eventId()).isEqualTo(repeated.eventId());
	}

	@Test
	void ownsCocoCallbackTokenAndResponseContract() {
		Map<String, String> parameters = Map.of("ext", "coco-token", "action_type", "activate");

		var success = adapter.callbackSuccess(parameters, "event-1", "ACCEPTED").block();
		var failure = adapter.callbackFailure(parameters, 400, "bad callback").block();

		assertThat(adapter.callbackToken(parameters).block()).isEqualTo("coco-token");
		assertThat(success.statusCode()).isEqualTo(200);
		assertThat(success.body()).isEqualTo(Map.of("code", 0, "event_id", "event-1", "status", "ACCEPTED"));
		assertThat(failure.statusCode()).isEqualTo(400);
		assertThat(failure.body()).isEqualTo(Map.of("code", 1, "message", "bad callback"));
	}

	private AdapterHttpResponse response(int status, String body) {
		return new AdapterHttpResponse(status, Map.of(), body);
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
