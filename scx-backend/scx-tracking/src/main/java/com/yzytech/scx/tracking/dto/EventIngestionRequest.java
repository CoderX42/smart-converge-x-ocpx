package com.yzytech.scx.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class EventIngestionRequest {

	@NotBlank
	private String schemaVersion;

	@NotBlank
	private String eventId;

	@NotBlank
	private String taskCode;

	@NotBlank
	private String eventType;

	@NotNull
	private OffsetDateTime occurredAt;

	@NotNull
	private DeviceIdentifier device;

	private String businessEventKey;

	private String callbackRef;

	private String callbackUrl;

	private Map<String, Object> attributes;

}
