package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MediaRequests {

	private MediaRequests() {
	}

	public record Create(@NotBlank @Size(max = 64) String mediaCode, @NotBlank @Size(max = 128) String mediaName,
			@NotBlank @Size(max = 64) String adapterCode,
			@NotBlank @Pattern(regexp = "NONE|TOKEN|SIGNATURE") String authType,
			@Size(max = 128) String credentialConfigKey, @Min(1) Long credentialVersion,
			@NotBlank @Size(max = 255) String ingressUri, @Size(max = 4000) String macroParams,
			@Size(max = 2000) String callbackUrl, @Size(max = 2000) String callbackHostAllowlist) {
	}

	public record Update(@NotBlank @Size(max = 128) String mediaName,
			@NotBlank @Size(max = 64) String adapterCode,
			@NotBlank @Pattern(regexp = "NONE|TOKEN|SIGNATURE") String authType,
			@Size(max = 128) String credentialConfigKey, @Min(1) Long credentialVersion,
			@NotBlank @Size(max = 255) String ingressUri, @Size(max = 4000) String macroParams,
			@Size(max = 2000) String callbackUrl, @Size(max = 2000) String callbackHostAllowlist,
			@NotNull @Min(1) Long expectedVersion) {
	}

	public record StatusAction(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
			@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

}
