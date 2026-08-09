package com.yzytech.scx.adx.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class EventRequests {

	private EventRequests() {
	}

	public record SaveDefinition(@NotBlank @Pattern(regexp = "UPSTREAM|CONVERSION|MEDIA") String category,
			String channelId, @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$") String eventCode,
			@NotBlank @Size(max = 100) String eventName,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status, @NotNull @Min(0) Integer sortOrder,
			@Size(max = 500) String remark) {
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

}
