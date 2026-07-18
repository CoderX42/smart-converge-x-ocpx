package com.yzytech.scx.tracking.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EventOccurredAtDeserializerTest {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void acceptsIso8601Timestamp() throws Exception {
		EventIngestionRequest request = objectMapper.readValue(payload("2026-07-16T14:30:00"),
				EventIngestionRequest.class);

		assertThat(request.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 7, 16, 14, 30));
	}

	@Test
	void acceptsLegacyTimestamp() throws Exception {
		EventIngestionRequest request = objectMapper.readValue(payload("2026-07-16 14:30:00"),
				EventIngestionRequest.class);

		assertThat(request.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 7, 16, 14, 30));
	}

	private String payload(String occurredAt) {
		return "{\"schemaVersion\":\"1.0\",\"eventId\":\"event-1\",\"source\":\"sandbox\","
				+ "\"taskCode\":\"demo-task\",\"eventType\":\"CLICK\",\"occurredAt\":\"" + occurredAt
				+ "\",\"deviceKeyHash\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}";
	}

}
