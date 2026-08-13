package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PromotionLinkRequests {

	private PromotionLinkRequests() {
	}

	public record Create(@NotBlank String advertiserId, @NotBlank @Size(max = 128) String linkName,
			@NotNull @Min(1) @Max(4) Integer platform, @Size(max = 64) String linkChannel,
			@NotBlank @Size(max = 5000) String linkUrl, @Size(max = 2000) String deeplink,
			@Size(max = 4000) String linkExt, @Size(max = 500) String remark) {
	}

	public record Update(@NotBlank String advertiserId, @NotBlank @Size(max = 128) String linkName,
			@NotNull @Min(1) @Max(4) Integer platform, @Size(max = 64) String linkChannel,
			@NotBlank @Size(max = 5000) String linkUrl, @Size(max = 2000) String deeplink,
			@Size(max = 4000) String linkExt, @Size(max = 500) String remark) {
	}

}
