package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public final class MonitorTaskTestRequests {

	private MonitorTaskTestRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Generate {

		@NotBlank
		@Pattern(regexp = "ANDROID|IOS")
		private String os;

		@Size(max = 24)
		private List<@Pattern(regexp = "[a-z][a-z0-9_]{0,31}") String> parameters;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Execute {

		@NotBlank
		@Size(max = 128)
		private String requestId;

		@NotBlank
		@Pattern(regexp = "ANDROID|IOS")
		private String os;

		@NotBlank
		@Pattern(regexp = "[0-9a-f]{32}")
		private String callbackToken;

		@NotNull
		@Size(max = 24)
		private Map<String, String> parameters;

	}

}
