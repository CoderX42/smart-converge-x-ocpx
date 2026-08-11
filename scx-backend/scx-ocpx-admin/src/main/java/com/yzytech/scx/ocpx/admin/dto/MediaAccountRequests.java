package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class MediaAccountRequests {

	private MediaAccountRequests() {
	}

	public record Create(@NotBlank String channelId, @NotBlank @Size(max = 64) String name,
			@Size(max = 32) String documentId, @Size(max = 500) String description) {
	}

	public record Update(@NotBlank @Size(max = 64) String name, @Size(max = 32) String documentId,
			@Size(max = 500) String description, @NotNull @Min(1) Long expectedVersion) {
	}

	public record ChangeStatus(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED|PAUSED") String status,
			@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public record CreateCredential(@NotBlank @Pattern(regexp = "EMAIL") String loginType,
			@NotBlank @Email @Size(max = 255) String loginIdentifier,
			@NotBlank @Size(min = 8, max = 4096) String secretInput, @Size(max = 500) String description) {
	}

	public record UpdateCredential(@Email @Size(max = 255) String loginIdentifier, @Size(max = 500) String description,
			@NotNull @Min(1) Long expectedVersion) {
	}

	public record RotateSecret(@NotBlank @Size(min = 8, max = 4096) String secretInput,
			@NotNull @Min(1) Long expectedSecretRevision, @NotBlank @Size(max = 255) String reason) {
	}

	public record ChangeCredentialStatus(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED|REVOKED") String status,
			@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public record ReplaceOwners(@NotNull @Min(1) Long expectedVersion,
			@NotNull @Size(max = 50) List<@NotBlank String> userIds) {
	}

	public record Preflight(@NotNull @Min(1) Long expectedVersion) {
	}

}
