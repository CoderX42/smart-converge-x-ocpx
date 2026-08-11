package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TaskRequests {

	private TaskRequests() {
	}

	public record Create(@NotBlank String productId, @NotBlank String clickAppLinkId, String exposureAppLinkId,
			@NotBlank String mediaAccountId, @NotBlank String mediaCredentialId, @NotBlank String operatorUserId,
			@NotBlank @Size(max = 64) String taskName, @NotNull Boolean exposureEnabled,
			@NotNull Boolean exposureAsClick, @NotNull @Min(0) @Max(86400) Integer dedupWindowSeconds,
			@NotBlank @Pattern(regexp = "OFF") String rtaMode, @Size(max = 500) String description,
			@Valid @Size(max = 100) List<EventRequests.TaskBindingInput> eventBindings) {

		public Create(String productId, String clickAppLinkId, String exposureAppLinkId, String mediaAccountId,
				String mediaCredentialId, String operatorUserId, String taskName, Boolean exposureEnabled,
				Boolean exposureAsClick, Integer dedupWindowSeconds, String rtaMode, String description) {
			this(productId, clickAppLinkId, exposureAppLinkId, mediaAccountId, mediaCredentialId, operatorUserId,
					taskName, exposureEnabled, exposureAsClick, dedupWindowSeconds, rtaMode, description, null);
		}

		@AssertTrue(message = "曝光开关关闭时必须清空曝光链接和大点击，开启时必须选择曝光链接")
		public boolean isExposureConsistent() {
			return Boolean.TRUE.equals(exposureEnabled) ? exposureAppLinkId != null && !exposureAppLinkId.isBlank()
					: (exposureAppLinkId == null || exposureAppLinkId.isBlank())
							&& !Boolean.TRUE.equals(exposureAsClick);
		}
	}

	public record Update(@NotBlank String productId, @NotBlank String clickAppLinkId, String exposureAppLinkId,
			@NotBlank String mediaAccountId, @NotBlank String mediaCredentialId, @NotBlank String operatorUserId,
			@NotBlank @Size(max = 64) String taskName, @NotNull Boolean exposureEnabled,
			@NotNull Boolean exposureAsClick, @NotNull @Min(0) @Max(86400) Integer dedupWindowSeconds,
			@NotBlank @Pattern(regexp = "OFF") String rtaMode, @Size(max = 500) String description,
			@NotNull @Min(1) Long expectedVersion,
			@Valid @Size(max = 100) List<EventRequests.TaskBindingInput> eventBindings) {

		public Update(String productId, String clickAppLinkId, String exposureAppLinkId, String mediaAccountId,
				String mediaCredentialId, String operatorUserId, String taskName, Boolean exposureEnabled,
				Boolean exposureAsClick, Integer dedupWindowSeconds, String rtaMode, String description,
				Long expectedVersion) {
			this(productId, clickAppLinkId, exposureAppLinkId, mediaAccountId, mediaCredentialId, operatorUserId,
					taskName, exposureEnabled, exposureAsClick, dedupWindowSeconds, rtaMode, description,
					expectedVersion, null);
		}

		@AssertTrue(message = "曝光开关关闭时必须清空曝光链接和大点击，开启时必须选择曝光链接")
		public boolean isExposureConsistent() {
			return Boolean.TRUE.equals(exposureEnabled) ? exposureAppLinkId != null && !exposureAppLinkId.isBlank()
					: (exposureAppLinkId == null || exposureAppLinkId.isBlank())
							&& !Boolean.TRUE.equals(exposureAsClick);
		}
	}

	public record StatusAction(@NotNull @Min(1) Long expectedVersion,
			@NotBlank @Pattern(regexp = "DRAFT|READY|ACTIVE|PAUSED") String status, @Size(max = 255) String reason) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion, @Size(max = 255) String reason) {
	}

}
