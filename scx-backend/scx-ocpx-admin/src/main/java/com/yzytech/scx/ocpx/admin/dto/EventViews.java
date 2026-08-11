package com.yzytech.scx.ocpx.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EventViews {

	private EventViews() {
	}

	public record DefinitionDetail(String id, String code, String name, String direction, String standardType,
			boolean billingEligible, String subjectType, String status, int sortOrder, String description,
			long configVersion, String mappingState, ReferenceSummary references, LocalDateTime createTime,
			LocalDateTime updateTime) {
	}

	public record MappingDetail(String id, String channelId, String channelName, String adapterCode,
			long adapterCapabilityRevision, String externalCode, String direction, String eventDefinitionId,
			String eventDefinitionCode, String eventDefinitionName, String parameterTemplateCode,
			String successRuleCode, String status, String description, long configVersion, String mappingState,
			ReferenceSummary references, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record ReferenceSummary(long productDefaultCount, long taskBindingCount, long publishedSnapshotCount,
			boolean codeLocked, boolean statusChangeRequiresConfirmation) {
	}

	public record StatusResult(String id, String status, long configVersion, ReferenceSummary references,
			String effect) {
	}

	public record DefinitionOption(String id, String code, String name, String direction, String standardType,
			boolean billingEligible, long configVersion) {
	}

	public record MappingOption(String id, String channelId, String adapterCode, String externalCode, String direction,
			String eventDefinitionId, String parameterTemplateCode, long configVersion) {
	}

	public record ProductTemplate(String productPolicyId, long productPolicyRevision, String eventDefinitionId,
			String eventCode, String eventName, String channelEventMappingId, Long limitValue, boolean applicable,
			String message) {
	}

	public record TaskBindingOptions(List<DefinitionOption> definitions, List<MappingOption> mappings,
			List<ProductTemplate> productTemplates, String channelId, String adapterCode, String limitWindow,
			String limitTimezone, String samplingAlgorithm) {
	}

	public record TaskBindingView(String id, String taskId, String channelId, String eventDefinitionId,
			String eventCode, String eventName, long eventDefinitionRevision, String channelEventMappingId,
			String externalCode, long channelEventMappingRevision, int callbackRatioBasisPoints, Long limitValue,
			String limitWindow, String limitTimezone, boolean overLimitDelivery, String samplingAlgorithm,
			String sourceType, String sourceId, long sourceRevision, String status, long configVersion) {
	}

	public record TaskBindingsResult(String taskId, long taskConfigVersion, boolean republishRequired,
			int affectedMonitorCount, List<TaskBindingView> bindings) {
	}

	public record SnapshotItem(String taskEventBindingId, long taskEventBindingRevision, String eventDefinitionId,
			long eventDefinitionRevision, String eventMappingId, long eventMappingRevision, String standardEventCode,
			String direction, String externalEventCode, String parameterTemplateCode, int callbackRatioBasisPoints,
			Long limitValue, String limitWindow, String limitTimezone, boolean overLimitDelivery,
			String samplingAlgorithm, String fingerprint) {
	}

	public record SnapshotPreview(int schemaVersion, String mode, String taskId, long taskConfigRevision,
			boolean immutable, boolean persisted, boolean outboxCreated, List<SnapshotItem> items,
			List<String> validationErrors) {
	}

	public record Definition(String id, String category, String channelId, String channelName, String eventCode,
			String eventName, String status, Integer sortOrder, String remark, boolean builtIn, boolean referenced,
			long draftReferences, long publishedReferences, LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Mapping(String id, String channelId, String channelName, String upstreamEventId,
			String upstreamEventCode, String upstreamEventName, String conversionEventId, String conversionEventCode,
			String conversionEventName, String mediaEventId, String mediaEventCode, String mediaEventName,
			String status, String remark, long draftReferences, long publishedReferences, LocalDateTime createTime,
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
