package com.yzytech.scx.tracking.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventOccurredAtDeserializerTest {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void acceptsRfc3339TimestampWithTimezone() throws Exception {
		EventIngestionRequest request = objectMapper.readValue(payload("2026-07-16T14:30:00+08:00"),
				EventIngestionRequest.class);

		assertThat(request.getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-07-16T14:30:00+08:00"));
	}

	@Test
	void rejectsTimestampWithoutTimezone() {
		assertThatThrownBy(() -> objectMapper.readValue(payload("2026-07-16T14:30:00"), EventIngestionRequest.class))
			.isInstanceOf(Exception.class);
	}

	private String payload(String occurredAt) {
		return "{\"schemaVersion\":\"1.1\",\"eventId\":\"event-1\",\"taskCode\":\"demo-task\","
				+ "\"eventType\":\"CLICK\",\"occurredAt\":\"" + occurredAt + "\",\"device\":{"
				+ "\"platform\":\"ANDROID\",\"idType\":\"OAID\",\"hashAlgorithm\":\"SHA256\"," + "\"idHash\":\""
				+ "a".repeat(64) + "\"}}";
	}

}
