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

}
