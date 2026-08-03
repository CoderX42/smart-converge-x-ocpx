package com.yzytech.scx.ocpx.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record CallbackReqV1(@JsonProperty("schema_version") String schemaVersion,
		@JsonProperty("event_id") String eventId, @JsonProperty("source_event_id") String sourceEventId, String event,
		@JsonProperty("action_time") OffsetDateTime actionTime, BigDecimal amount,
		@JsonProperty("amount_raw") String amountRaw, String clickid, @JsonProperty("monitor_id") String monitorId,
		@JsonProperty("channel_id") String channelId, Map<String, Object> ext) {
}
