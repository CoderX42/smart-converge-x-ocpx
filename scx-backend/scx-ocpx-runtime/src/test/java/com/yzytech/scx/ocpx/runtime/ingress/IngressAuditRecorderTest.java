package com.yzytech.scx.ocpx.runtime.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

class IngressAuditRecorderTest {

	@Test
	void persistsOnlyHashesAndSanitizedReasons() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		TaskExecutor directExecutor = Runnable::run;
		IngressAuditRecorder recorder = new IngressAuditRecorder(jdbcTemplate, new ObjectMapper(), directExecutor);
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Request-Id", "request-1");

		recorder.record("demo", "AUTH_REJECTED", 401,
				"signature=0123456789abcdef0123456789abcdef callback=private-token",
				"{\"event_id\":\"evt-1\",\"monitor_id\":\"monitor-1\",\"oaid\":\"device-secret\"}", headers,
				"192.168.10.20", 9);

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(anyString(), arguments.capture());
		Object[] values = arguments.getValue();
		assertThat(values[7].toString()).contains("signature=[REDACTED]", "callback=[REDACTED]")
			.doesNotContain("private-token", "0123456789abcdef");
		assertThat(values[8].toString()).hasSize(64).doesNotContain("device-secret");
		assertThat(values[11]).isEqualTo("192.168.***.***");
	}

	@Test
	void persistsCallbackMetadataWithoutRawQueryValues() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		TaskExecutor directExecutor = Runnable::run;
		IngressAuditRecorder recorder = new IngressAuditRecorder(jdbcTemplate, new ObjectMapper(), directExecutor);
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Request-Id", "callback-request-1");
		java.util.Map<String, String> parameters = new java.util.LinkedHashMap<String, String>();
		parameters.put("ext", "private-callback-token");
		parameters.put("s", "private-signature");
		parameters.put("conversion_id", "external-order-10000001");

		recorder.recordUpstreamCallback(" coco ", "PROTOCOL_REJECTED", 400,
				"ext=private-callback-token signature=private-signature", parameters, headers, "10.20.30.40", 12);

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(anyString(), arguments.capture());
		Object[] values = arguments.getValue();
		assertThat(values[2]).isEqualTo("UPSTREAM:COCO");
		assertThat(values[3].toString()).startsWith("external-sha256:").doesNotContain("external-order-10000001");
		assertThat(values[4]).isEqualTo("upstream:COCO");
		assertThat(values[5]).isEqualTo("PROTOCOL_REJECTED");
		assertThat(values[7].toString()).contains("ext=[REDACTED]", "signature=[REDACTED]")
			.doesNotContain("private-callback-token", "private-signature");
		assertThat(values[8].toString()).hasSize(64)
			.doesNotContain("private-callback-token", "private-signature", "external-order-10000001");
		assertThat(values[9]).isEqualTo("COCO");
		assertThat(values[11]).isEqualTo("10.20.***.***");
	}

}
