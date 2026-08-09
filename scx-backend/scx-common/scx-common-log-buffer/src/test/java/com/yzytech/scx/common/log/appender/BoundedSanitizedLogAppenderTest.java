package com.yzytech.scx.common.log.appender;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedSanitizedLogAppenderTest {

	@Test
	void sanitizesSecretsAndPersonalIdentifiersBeforeQuery() {
		BoundedSanitizedLogAppender.record(System.currentTimeMillis(), "INFO", "test.api-debug-buffer",
				"Authorization: Bearer should-not-leak phone=13800138000 "
						+ "json={\"password\":\"also-secret\"} jdbc:mysql://db.example/test safe=visible",
				null);

		BoundedSanitizedLogAppender.QueryResult result = BoundedSanitizedLogAppender.query("INFO",
				"test.api-debug-buffer", "safe=visible", 0);

		assertThat(result.records()).hasSize(1);
		assertThat(result.records().get(0).message())
			.contains("Authorization: [REDACTED]", "phone=[REDACTED]", "\"password\":[REDACTED]",
					"[REDACTED_CONNECTION_STRING]", "safe=visible")
			.doesNotContain("should-not-leak", "13800138000", "also-secret", "db.example");
	}

}
