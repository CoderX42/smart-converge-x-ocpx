package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class RuntimeRequests {

	private RuntimeRequests() {
	}

	public record Query(Long current, Long size, LocalDateTime startTime, LocalDateTime endTime, String factType,
			String direction, String status, String failureClass, String advertiserId, String productId,
			String appLinkId, String taskId, String channelId, String mediaAccountId, String routeVersionId,
			String eventCode, String exactId, String traceId, String sort) {
	}

	public record ReplayPreview(@NotBlank @Size(max = 32) String failureId,
			@NotNull @Min(1) Long expectedFailureRevision) {
	}

	public record ReplayConfirm(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$") String requestId,
			@NotBlank @Size(max = 32) String failureId, @NotNull @Min(1) Long expectedFailureRevision,
			@NotBlank @Size(max = 4096) String previewToken, @NotBlank @Size(min = 4, max = 500) String reason,
			@Size(max = 128) String ticket, @NotNull Boolean confirmed) {

		@AssertTrue(message = "补偿命令必须显式确认")
		public boolean isExplicitlyConfirmed() {
			return Boolean.TRUE.equals(confirmed);
		}
	}

}
