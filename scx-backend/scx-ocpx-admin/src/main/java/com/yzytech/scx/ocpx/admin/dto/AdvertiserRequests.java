package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdvertiserRequests {

	private AdvertiserRequests() {
	}

	public record Create(@NotBlank @Size(max = 64) String name,
			@NotBlank @Pattern(regexp = "^[a-z0-9_-]{2,32}$") String code,
			@NotBlank @Size(max = 32) String adapterKey, @Size(max = 5000) String bidUrl,
			@Size(max = 1000) String macroParams, @Size(max = 1000) String upstreamHostAllowlist,
			@NotBlank @Size(max = 100) String account, @NotBlank @Size(min = 8, max = 72) String password,
			@Size(min = 16, max = 4096) String credentialInput) {
	}

	public record Update(@NotBlank @Size(max = 64) String name, @NotBlank @Size(max = 32) String adapterKey,
			@Size(max = 5000) String bidUrl, @Size(max = 1000) String macroParams,
			@Size(max = 1000) String upstreamHostAllowlist, @NotNull Long expectedVersion) {
	}

	public record RotateCredential(@NotBlank @Size(min = 16, max = 4096) String credentialInput,
			@NotNull Long expectedCredentialRevision, @NotBlank @Size(max = 255) String reason) {
	}

	public record ChangeStatus(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
			@NotNull Long expectedVersion, @NotBlank @Size(max = 255) String reason, boolean confirmed) {
	}

	public record SetCurrentDocument(@NotNull Long expectedVersion) {
	}

	public record RetireDocument(@NotBlank @Size(max = 255) String reason) {
	}

}
