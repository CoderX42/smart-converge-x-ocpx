package com.yzytech.scx.tracking.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yzytech.scx.tracking.serialization.EventOccurredAtDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EventIngestionRequest {

	@NotBlank
	private String schemaVersion;

	@NotBlank
	private String eventId;

	@NotBlank
	private String source;

	@NotBlank
	private String taskCode;

	@NotBlank
	private String eventType;

	@NotNull
	@JsonDeserialize(using = EventOccurredAtDeserializer.class)
	private LocalDateTime occurredAt;

	@NotBlank
	private String deviceKeyHash;

	private String businessEventKey;

	private Map<String, Object> attributes;

}
