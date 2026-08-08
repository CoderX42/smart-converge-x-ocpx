package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateGetUpstreamAdapterTest {

	private final TemplateGetUpstreamAdapter adapter = new TemplateGetUpstreamAdapter(new GetMacroTemplateRenderer());

	@Test
	void buildsGenericFeedbackFromStandardMacros() {
		FeedbackReqV1 feedback = new FeedbackReqV1();
		feedback.setEvent("click");
		feedback.setEventId("event-1");
		feedback.setEventTime(1_785_744_000_000L);
		feedback.setMonitorId("monitor-1");
		feedback.setClickid("click-1");
		feedback.setOaid("oaid value");

		var request = adapter.buildFeedback(route(
				"https://upstream.example/feedback?event=__EVENT__&event_id=__EVENT_ID__&biz_id=__BIZ_ID__&mts=__MTS__&oaid=__OAID__&cb=__CALLBACK__",
				"__EVENT_ID__,__CALLBACK__"), feedback, "https://scx.example/ocpx/callback?ext=token value")
			.block();

		assertThat(request.method()).isEqualTo("GET");
		assertThat(request.uri().toString()).contains("event=click", "event_id=event-1", "biz_id=biz-1",
				"mts=1785744000000", "oaid=oaid%20value", "ext%3Dtoken%20value");
	}

	@Test
	void rejectsMissingRequiredMacroValue() {
		FeedbackReqV1 feedback = new FeedbackReqV1();
		feedback.setEvent("show");

		assertThatThrownBy(
				() -> adapter
					.buildFeedback(route("https://upstream.example/feedback?clickid=__CLICKID__&cb=__CALLBACK__",
							"__CLICKID__"), feedback, "https://scx.example/callback")
					.block())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("__CLICKID__");
	}

	@Test
	void parsesSgyzStyleCallbackVocabularyAndKeepsStableIdentity() {
		Map<String, String> parameters = Map.of("ext", "token", "event", "authentication", "event_id", "conv-1",
				"event_time", "1785744000", "amount", "12.34");

		var first = adapter.parseCallback("callback-ref", parameters).block();
		var retry = adapter.parseCallback("callback-ref", parameters).block();

		assertThat(first.eventType()).isEqualTo(OcpxEventType.AUTHENTICATION);
		assertThat(first.eventId()).isEqualTo(retry.eventId());
		assertThat(first.actionTime().toInstant()).isEqualTo(Instant.ofEpochSecond(1_785_744_000L));
		assertThat(first.amount()).isEqualByComparingTo("12.34");
	}

	@Test
	void ownsTemplateCallbackTokenAndResponseContract() {
		Map<String, String> parameters = Map.of("ext", "template-token", "event", "activate");

		var success = adapter.callbackSuccess(parameters, "event-1", "DUPLICATE").block();
		var failure = adapter.callbackFailure(parameters, 422, "invalid event").block();

		assertThat(adapter.callbackToken(parameters).block()).isEqualTo("template-token");
		assertThat(success.statusCode()).isEqualTo(200);
		assertThat(success.body()).isEqualTo(Map.of("code", 0, "event_id", "event-1", "status", "DUPLICATE"));
		assertThat(failure.statusCode()).isEqualTo(422);
		assertThat(failure.body()).isEqualTo(Map.of("code", 1, "message", "invalid event"));
	}

	private RouteSnapshot route(String bidUrl, String requiredMacros) {
		return new RouteSnapshot("monitor-1", "biz-1", 1, "channel-1", "channel", "STANDARD", "HMAC", 1,
				"channel-secret", "advertiser-1", "app-1", "TEMPLATE_GET", null, bidUrl, requiredMacros,
				"https://downstream.example/callback", BigDecimal.ONE, BigDecimal.ONE, "OFF", "OFF");
	}

}
