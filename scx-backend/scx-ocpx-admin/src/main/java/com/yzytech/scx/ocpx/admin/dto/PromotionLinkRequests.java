package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PromotionLinkRequests {

	private PromotionLinkRequests() {
	}

	public record Create(@NotBlank String advertiserId, @NotBlank @Size(max = 64) String linkCode,
			@NotBlank @Size(max = 128) String linkName, @Size(max = 255) String packageName,
			@NotBlank @Pattern(regexp = "ANDROID|IOS|WEB|OTHER") String platform,
			@NotBlank @Pattern(regexp = "LANDING|DOWNLOAD|DEEPLINK|OTHER") String linkType,
			@Size(max = 64) String linkChannel, @NotBlank @Size(max = 5000) String linkUrlTemplate,
			@Size(max = 2000) String deeplinkUrl, @Size(max = 2000) String downloadUrl,
			@NotNull Boolean redirectEnabled, @Size(max = 4000) String macroSet, @Size(max = 500) String remark) {
	}

	public record Update(@NotBlank String advertiserId, @NotBlank @Size(max = 128) String linkName,
			@Size(max = 255) String packageName,
			@NotBlank @Pattern(regexp = "ANDROID|IOS|WEB|OTHER") String platform,
			@NotBlank @Pattern(regexp = "LANDING|DOWNLOAD|DEEPLINK|OTHER") String linkType,
			@Size(max = 64) String linkChannel, @NotBlank @Size(max = 5000) String linkUrlTemplate,
			@Size(max = 2000) String deeplinkUrl, @Size(max = 2000) String downloadUrl,
			@NotNull Boolean redirectEnabled, @Size(max = 4000) String macroSet, @Size(max = 500) String remark,
			@NotNull @Min(1) Long expectedVersion) {
	}

	public record StatusAction(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
			@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

	public record ValidateUrl(@NotBlank @Size(max = 5000) String url) {
	}

}
