package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ProductRequests {

	private ProductRequests() {
	}

	public record Create(@NotBlank String advertiserId,
			@NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{1,63}$") String code,
			@NotBlank @Size(max = 64) String name, @Size(max = 255) String packageName,
			@Size(max = 500) String description,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
			@Size(max = 100) List<@Valid EventPolicy> eventPolicies) {
	}

	public record Update(@NotBlank @Size(max = 64) String name, @Size(max = 255) String packageName,
			@Size(max = 500) String description,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
			@NotNull @Min(1) Long expectedVersion,
			@Size(max = 100) List<@Valid EventPolicy> eventPolicies) {
	}

	public record EventPolicy(@NotBlank String eventDefinitionId,
			@NotNull @Min(0) @Max(2147483647) Integer dailyLimit) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public static List<EventPolicy> safePolicies(List<EventPolicy> policies) {
		return policies == null ? List.of() : List.copyOf(policies);
	}

}
