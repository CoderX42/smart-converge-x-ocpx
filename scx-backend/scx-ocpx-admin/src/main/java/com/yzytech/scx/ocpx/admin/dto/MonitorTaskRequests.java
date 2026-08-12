package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MonitorTaskRequests {

	private MonitorTaskRequests() {
	}

	public record Create(@NotBlank @Size(max = 64) String taskCode, @NotBlank @Size(max = 128) String taskName,
			@NotBlank String advertiserId, @NotBlank String promotionLinkId, @NotBlank String mediaId,
			String operatorUserId) {
	}

	public record Update(@NotBlank @Size(max = 128) String taskName, @NotBlank String advertiserId,
			@NotBlank String promotionLinkId, @NotBlank String mediaId, String operatorUserId,
			@NotNull @Min(1) Long expectedVersion) {
	}

	public record ChangeStatus(@NotBlank String status, @NotNull @Min(1) Long expectedVersion) {
	}

	public record VersionedAction(@NotNull @Min(1) Long expectedVersion) {
	}

}
