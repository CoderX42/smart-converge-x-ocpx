package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AppLinkRequests {

	private AppLinkRequests() {
	}

	public record Create(@NotBlank String productId, @NotBlank String documentId,
			@NotBlank @Pattern(regexp = "ANDROID|IOS|OTHER") String linkType, @NotBlank @Size(max = 64) String linkName,
			@NotBlank @Size(max = 5000) String linkUrlTemplate, @NotNull Boolean redirectEnabled,
			@Size(max = 2000) String deeplinkUrl, @Size(max = 2000) String downloadUrl,
			@Size(max = 500) String description) {
	}

	public record Update(@NotBlank String productId, @NotBlank String documentId,
			@NotBlank @Pattern(regexp = "ANDROID|IOS|OTHER") String linkType, @NotBlank @Size(max = 64) String linkName,
			@Size(max = 5000) String linkUrlTemplate, @NotNull Boolean redirectEnabled,
			@Size(max = 2000) String deeplinkUrl, @Size(max = 2000) String downloadUrl, Boolean clearDeeplink,
			Boolean clearDownload, @Size(max = 500) String description, @NotNull @Min(0) Long expectedVersion) {
	}

	public record CompleteLegacyMapping(@NotBlank String productId, @NotBlank String documentId,
			@NotBlank @Pattern(regexp = "ANDROID|IOS|OTHER") String linkType, @NotBlank @Size(max = 64) String linkName,
			@NotBlank @Size(max = 5000) String linkUrlTemplate, @NotNull Boolean redirectEnabled,
			@Size(max = 2000) String deeplinkUrl, @Size(max = 2000) String downloadUrl,
			@Size(max = 500) String description, @NotNull @Min(0) Long expectedVersion) {
	}

	public record StatusAction(@NotNull @Min(1) Long expectedVersion,
			@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status, @Size(max = 255) String reason) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public record ValidateUrl(@NotBlank @Pattern(regexp = "TEMPLATE|DEEPLINK|DOWNLOAD") String kind,
			@NotBlank @Pattern(regexp = "ANDROID|IOS|OTHER") String linkType, @NotBlank @Size(max = 5000) String url) {
	}

}
