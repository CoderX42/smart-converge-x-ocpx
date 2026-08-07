package com.yzytech.scx.ocpx.runtime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.CallbackReqV1;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

class StandardDownstreamAdapterTest {

	private final StandardDownstreamAdapter adapter = new StandardDownstreamAdapter(
			new ObjectMapper().registerModule(new JavaTimeModule()));

	@Test
	void parsesFeedbackReqAndRejectsMissingEvent() {
		FeedbackReqV1 parsed = adapter.parseFeedback("""
				{"event":"click","monitor_id":"monitor-1","oaid":"device"}
				""");

		assertThat(parsed.getEvent()).isEqualTo("click");
		assertThat(parsed.getMonitorId()).isEqualTo("monitor-1");
		assertThatThrownBy(() -> adapter.parseFeedback("{}")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("event");
	}

	@Test
	void signsSnakeCaseCallbackWithStableIdempotencyKey() {
		CallbackReqV1 callback = new CallbackReqV1("1.0", "callback-1", "feedback-1", "ACTIVATE",
				OffsetDateTime.parse("2026-08-03T09:00:00Z"), null, null, "click-1", "monitor-1", "channel-1",
				Map.of());

		AdapterHttpRequest request = adapter.buildCallback(route(), callback);

		assertThat(request.headers()).containsEntry("Idempotency-Key", "callback-1");
		assertThat(request.headers()).containsKeys("X-Client-Id", "X-Key-Version", "X-Timestamp", "X-Nonce", "X-Sign");
		assertThat(request.body()).contains("\"schema_version\":\"1.0\"", "\"event_id\":\"callback-1\"");
		assertThat(request.body()).doesNotContain("12345678901234567890123456789012");
	}

	private RouteSnapshot route() {
		return new RouteSnapshot("monitor-1", "biz-1", 3, "channel-1", "demo", "STANDARD", "HMAC", 1,
				"12345678901234567890123456789012", "advertiser-1", "app-1", "COCO", null, "https://coco.example/click",
				null, "https://callback.example/postback", BigDecimal.ONE, BigDecimal.ONE, "OFF", "OFF");
	}

}
