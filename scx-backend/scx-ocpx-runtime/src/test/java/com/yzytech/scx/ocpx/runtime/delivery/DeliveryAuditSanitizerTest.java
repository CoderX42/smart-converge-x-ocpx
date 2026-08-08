package com.yzytech.scx.ocpx.runtime.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

class DeliveryAuditSanitizerTest {

	private final DeliveryAuditSanitizer sanitizer = new DeliveryAuditSanitizer();

	@Test
	void retainsOperationalMetadataButNeverRawQueryOrBody() {
		AdapterHttpRequest request = new AdapterHttpRequest("GET",
				URI.create("https://coco.example/click?oaid=device-secret&callback=callback-token"), Map.of(), null);
		AdapterHttpResponse response = new AdapterHttpResponse(200, Map.of(),
				"{\"code\":402,\"token\":\"response-secret\"}");

		var audit = sanitizer.summarize(request, response, 18);

		assertThat(audit.targetHost()).isEqualTo("coco.example");
		assertThat(audit.targetPath()).isEqualTo("/click");
		assertThat(audit.requestSummary()).contains("query_sha256=", "query_bytes=")
			.doesNotContain("device-secret", "callback-token");
		assertThat(audit.responseExcerpt()).contains("body_sha256=", "body_bytes=").doesNotContain("response-secret");
	}

	@Test
	void redactsCallbackTokenStoredAsLongPathSegment() {
		String callbackToken = "8e56dae0-1ff3-45e5-8ae9-0159199d8a56";
		AdapterHttpRequest request = new AdapterHttpRequest("POST",
				URI.create("https://webhook.example/api/" + callbackToken), Map.of(), "{}");

		var audit = sanitizer.summarize(request, new AdapterHttpResponse(200, Map.of(), ""), 9);

		assertThat(audit.targetPath()).startsWith("/api/{redacted_sha256=")
			.endsWith(";bytes=36}")
			.doesNotContain(callbackToken);
		assertThat(audit.requestSummary()).doesNotContain(callbackToken);
	}

}
