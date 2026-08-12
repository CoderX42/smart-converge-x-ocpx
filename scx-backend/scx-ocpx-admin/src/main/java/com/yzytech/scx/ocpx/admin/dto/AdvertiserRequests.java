package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdvertiserRequests {

	private AdvertiserRequests() {
	}

	public record Create(@NotBlank @Size(max = 128) String name,
			@NotBlank @Pattern(regexp = "^[a-z0-9_-]{2,64}$") String code,
			@NotBlank @Size(max = 64) String adapterCode, @NotBlank @Size(max = 128) String credentialConfigKey,
			@NotNull @Min(1) Long credentialVersion, @NotBlank @Size(max = 5000) String bidUrl,
			@Size(max = 4000) String macroParams, @Size(max = 2000) String upstreamHostAllowlist) {
	}

	public record Update(@NotBlank @Size(max = 128) String name, @NotBlank @Size(max = 64) String adapterCode,
			@NotBlank @Size(max = 128) String credentialConfigKey, @NotNull @Min(1) Long credentialVersion,
			@NotBlank @Size(max = 5000) String bidUrl, @Size(max = 4000) String macroParams,
			@Size(max = 2000) String upstreamHostAllowlist, @NotNull @Min(1) Long expectedVersion) {
	}

	public record ChangeStatus(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
			@NotNull @Min(1) Long expectedVersion) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion) {
	}

}
