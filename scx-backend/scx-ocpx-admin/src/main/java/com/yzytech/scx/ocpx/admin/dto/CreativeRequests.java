package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public final class CreativeRequests {

	private CreativeRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Save {

		@NotBlank
		private String advertiserId;

		@NotBlank
		@Size(max = 128)
		private String creativeName;

		@NotBlank
		private String sourceMaterialId;

		@NotNull
		@Min(0)
		@Max(1)
		private Integer status;

		@NotNull
		private List<@NotBlank String> promotionLinkIds = new ArrayList<>();

		@Size(max = 500)
		private String remark;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Bindings {

		@NotNull
		private List<@NotBlank String> promotionLinkIds = new ArrayList<>();

	}

}
