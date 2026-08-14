package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class DeductionRuleRequests {

	private DeductionRuleRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Create {

		@NotBlank
		@Size(max = 32)
		private String mediaId;

		@NotBlank
		@Size(max = 32)
		private String promotionLinkId;

		@NotNull
		private Double ratio;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Update {

		@NotBlank
		@Size(max = 32)
		private String mediaId;

		@NotBlank
		@Size(max = 32)
		private String promotionLinkId;

		@NotNull
		private Double ratio;

	}

}
