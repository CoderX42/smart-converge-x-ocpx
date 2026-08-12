package com.yzytech.scx.ocpx.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record CallbackReqV2(@JsonProperty("schema_version") String schemaVersion,
		@JsonProperty("event_id") String eventId, @JsonProperty("source_event_id") String sourceEventId, String event,
		@JsonProperty("action_time") OffsetDateTime actionTime, BigDecimal amount,
		@JsonProperty("amount_raw") String amountRaw, @JsonProperty("click_id") String clickId,
		@JsonProperty("monitor_task_id") String monitorTaskId, Map<String, Object> extensions) {
}
