package com.yzytech.scx.adx.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RoutePublishRequests {

	private RoutePublishRequests() {
	}

	public record Publish(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$") String publishRequestId,
			@NotNull @Min(1) Long expectedTaskVersion, @NotBlank @Size(max = 4096) String checkToken) {
	}

	public record PausePreview(@NotNull @Min(1) Long expectedTaskVersion) {
	}

	public record Pause(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$") String pauseRequestId,
			@NotNull @Min(1) Long expectedTaskVersion, @NotBlank @Size(max = 4096) String previewToken,
			@NotNull Boolean confirmed, @NotBlank @Size(max = 255) String reason) {

		@AssertTrue(message = "停流命令必须显式确认")
		public boolean isExplicitlyConfirmed() {
			return Boolean.TRUE.equals(confirmed);
		}
	}

}
