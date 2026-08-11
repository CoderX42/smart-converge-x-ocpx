package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class EventRequests {

	private EventRequests() {
	}

	public record CreateDefinition(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String code,
			@NotBlank @Size(max = 64) String name,
			@NotBlank @Pattern(regexp = "UPSTREAM_CALLBACK|DOWNSTREAM_DELIVERY|BIDIRECTIONAL") String direction,
			@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String standardType, @NotNull Boolean billingEligible,
			@NotBlank @Pattern(regexp = "DEVICE|ACCOUNT|ORDER|LEAD") String subjectType,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status, @NotNull @Min(0) Integer sortOrder,
			@Size(max = 500) String description) {
	}

	public record UpdateDefinition(@NotBlank @Size(max = 64) String name,
			@NotBlank @Pattern(regexp = "UPSTREAM_CALLBACK|DOWNSTREAM_DELIVERY|BIDIRECTIONAL") String direction,
			@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String standardType, @NotNull Boolean billingEligible,
			@NotBlank @Pattern(regexp = "DEVICE|ACCOUNT|ORDER|LEAD") String subjectType,
			@NotNull @Min(0) Integer sortOrder, @Size(max = 500) String description,
			@NotNull @Min(1) Long expectedVersion) {
	}

	public record CreateMapping(@NotBlank String channelId, @NotBlank String adapterCode,
			@NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$") String externalCode,
			@NotBlank @Pattern(regexp = "UPSTREAM_CALLBACK|DOWNSTREAM_DELIVERY") String direction,
			@NotBlank String eventDefinitionId,
			@NotBlank @Pattern(regexp = "STANDARD_EVENT_V1|FLAT_EVENT_V1") String parameterTemplateCode,
			@NotBlank @Pattern(regexp = "HTTP_2XX|BODY_CODE_ZERO") String successRuleCode,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status, @Size(max = 500) String description) {
	}

	public record UpdateMapping(@NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$") String externalCode,
			@NotBlank @Pattern(regexp = "UPSTREAM_CALLBACK|DOWNSTREAM_DELIVERY") String direction,
			@NotBlank String eventDefinitionId,
			@NotBlank @Pattern(regexp = "STANDARD_EVENT_V1|FLAT_EVENT_V1") String parameterTemplateCode,
			@NotBlank @Pattern(regexp = "HTTP_2XX|BODY_CODE_ZERO") String successRuleCode,
			@Size(max = 500) String description, @NotNull @Min(1) Long expectedVersion) {
	}

	public record StatusAction(@NotNull @Min(1) Long expectedVersion,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED|ARCHIVED") String status, @NotNull Boolean confirmed,
			@Size(max = 255) String reason) {
	}

	public record TaskBindingInput(@NotBlank String eventDefinitionId, @NotBlank String channelEventMappingId,
			@NotNull @Min(0) @Max(10000) Integer callbackRatioBasisPoints, @PositiveOrZero Long limitValue,
			@NotNull Boolean overLimitDelivery) {
	}

	public record ReplaceTaskBindings(@NotNull @Min(1) Long expectedTaskVersion,
			@NotNull @Valid @Size(max = 100) List<TaskBindingInput> bindings) {
	}

	public record SaveDefinition(@NotBlank @Pattern(regexp = "UPSTREAM|CONVERSION|MEDIA") String category,
			String channelId, @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$") String eventCode,
			@NotBlank @Size(max = 100) String eventName, @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
			@NotNull @Min(0) Integer sortOrder, @Size(max = 500) String remark) {
	}

	public record SaveMapping(@NotBlank String channelId, @NotBlank String upstreamEventId,
			@NotBlank String conversionEventId, @NotBlank String mediaEventId,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status, @Size(max = 500) String remark) {
	}

	public record MonitorBinding(@NotBlank String eventMappingId,
			@NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 6) BigDecimal assessmentUnitPrice,
			@NotNull @Min(0) @Max(2147483647) Integer dailyLimit) {
	}

	public static List<MonitorBinding> safeBindings(List<MonitorBinding> bindings) {
		return bindings == null ? List.of() : List.copyOf(bindings);
	}

	public static List<TaskBindingInput> safeTaskBindings(List<TaskBindingInput> bindings) {
		return bindings == null ? List.of() : List.copyOf(bindings);
	}

}
