package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class PromotionLinkRequests {

	private PromotionLinkRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Create {

		@NotBlank
		private String advertiserId;

		@NotBlank
		@Size(max = 128)
		private String linkName;

		@NotNull
		@Min(1)
		@Max(4)
		private Integer platform;

		@Size(max = 64)
		private String linkChannel;

		@NotBlank
		@Size(max = 5000)
		private String linkUrl;

		@Size(max = 2000)
		private String deeplink;

		@Size(max = 4000)
		private String linkExt;

		@Size(max = 500)
		private String remark;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Update {

		@NotBlank
		private String advertiserId;

		@NotBlank
		@Size(max = 128)
		private String linkName;

		@NotNull
		@Min(1)
		@Max(4)
		private Integer platform;

		@Size(max = 64)
		private String linkChannel;

		@NotBlank
		@Size(max = 5000)
		private String linkUrl;

		@Size(max = 2000)
		private String deeplink;

		@Size(max = 4000)
		private String linkExt;

		@Size(max = 500)
		private String remark;

	}

}
