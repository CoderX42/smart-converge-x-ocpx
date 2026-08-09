package com.yzytech.scx.adx.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class EventViews {

	private EventViews() {
	}

	public record Definition(String id, String category, String channelId, String channelName, String eventCode,
			String eventName, String status, Integer sortOrder, String remark, boolean builtIn, boolean referenced,
			long draftReferences, long publishedReferences, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Mapping(String id, String channelId, String channelName, String upstreamEventId,
			String upstreamEventCode, String upstreamEventName, String conversionEventId, String conversionEventCode,
			String conversionEventName, String mediaEventId, String mediaEventCode, String mediaEventName, String status,
			String remark, long draftReferences, long publishedReferences, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record Option(String eventMappingId, String upstreamEventCode, String upstreamEventName,
			String bindingDisplayName, String conversionEventCode, String conversionEventName, String mediaEventCode,
			String mediaEventName) {
	}

	public record Binding(String id, String eventMappingId, String upstreamEventCode, String upstreamEventName,
			String bindingDisplayName, String conversionEventCode, String conversionEventName, String mediaEventCode,
			String mediaEventName, BigDecimal assessmentUnitPrice, Integer dailyLimit) {
	}

	public record BindingSummary(long bindingCount, String summary) {
	}

}
