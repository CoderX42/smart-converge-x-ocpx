package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DeductionRuleRequests {

	private DeductionRuleRequests() {
	}

	public record Create(@NotBlank @Size(max = 32) String mediaId, @NotBlank @Size(max = 32) String promotionLinkId,
			@NotNull Double ratio) {
	}

	public record Update(@NotBlank @Size(max = 32) String mediaId, @NotBlank @Size(max = 32) String promotionLinkId,
			@NotNull Double ratio) {
	}

}
