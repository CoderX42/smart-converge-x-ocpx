package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

public final class RtaAdminRequests {

	private RtaAdminRequests() {
	}

	@Data
	public static class Profile {

		@NotBlank
		@Size(max = 64)
		private String code;

		@NotBlank
		@Size(max = 128)
		private String name;

		@NotBlank
		@Pattern(regexp = "MOCK")
		private String adapterCode = "MOCK";

		@NotBlank
		@Pattern(regexp = "DISABLED|ACTIVE")
		private String status = "DISABLED";

		@Size(max = 4096)
		private String configJson;

	}

	@Data
	public static class TaskConfig {

		@NotNull
		private Boolean enabled = false;

		@Size(max = 32)
		private String profileId;

		@Valid
		@Size(max = 100)
		private List<Binding> bindings = List.of();

	}

	@Data
	public static class Binding {

		@NotBlank
		@Size(max = 128)
		private String externalTaskId;

		@Size(max = 128)
		private String externalProductId;

	}

	@Data
	public static class Query {

		private LocalDate startDate;

		private LocalDate endDate;

		@Pattern(regexp = "DAY|HOUR")
		private String grain = "DAY";

		@Size(max = 32)
		private String advertiserId;

		@Size(max = 32)
		private String mediaId;

		@Size(max = 32)
		private String promotionLinkId;

		@Size(max = 32)
		private String monitorTaskId;

		@Size(max = 32)
		private String profileId;

		private Long current = 1L;

		private Long size = 30L;

	}

}
