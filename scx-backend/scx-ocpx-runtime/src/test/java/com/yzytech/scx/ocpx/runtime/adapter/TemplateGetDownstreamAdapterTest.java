package com.yzytech.scx.ocpx.runtime.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.CallbackReqV1;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateGetDownstreamAdapterTest {

	private final TemplateGetDownstreamAdapter adapter = new TemplateGetDownstreamAdapter(
			new ObjectMapper().registerModule(new JavaTimeModule()));

	@Test
	void buildsEncodedIdempotentGetCallback() {
		CallbackReqV1 callback = new CallbackReqV1("1.0", "conversion/1", "click-1", "ACTIVATE",
				OffsetDateTime.parse("2026-08-07T09:00:00Z"), new BigDecimal("12.340000"), "12.34 CNY", "click id",
				"monitor-1", "channel-1", Map.of());

		AdapterHttpRequest request = adapter.buildCallback(
				route("""
						https://callback.example/notify?event=__EVENT__&event_id=__EVENT_ID__&clickid=__CLICKID__&amount=__AMOUNT__&ts=__TS__
						"""
					.trim()),
				callback);

		assertThat(request.method()).isEqualTo("GET");
		assertThat(request.uri().toString()).contains("event=ACTIVATE", "event_id=conversion/1", "clickid=click%20id",
				"amount=12.340000", "ts=1786093200");
		assertThat(request.headers()).containsEntry("Idempotency-Key", "conversion/1");
	}

	@Test
	void rejectsUnknownMacroAtRuntime() {
		CallbackReqV1 callback = new CallbackReqV1("1.0", "conversion-1", "click-1", "ACTIVATE",
				OffsetDateTime.parse("2026-08-07T09:00:00Z"), null, null, null, "monitor-1", "channel-1", Map.of());

		assertThatThrownBy(() -> adapter.buildCallback(route("https://callback.example?x=__PRIVATE_TOKEN__"), callback))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unresolved macros");
	}

	private RouteSnapshot route(String callbackUrl) {
		return new RouteSnapshot("monitor-1", "biz-1", 1, "channel-1", "channel", "TEMPLATE_GET_DOWNSTREAM", "HMAC", 1,
				"channel-secret", "advertiser-1", "app-1", "TEMPLATE_GET", null, "https://upstream.example/feedback",
				null, callbackUrl, BigDecimal.ONE, BigDecimal.ONE, "OFF", "OFF");
	}

}
