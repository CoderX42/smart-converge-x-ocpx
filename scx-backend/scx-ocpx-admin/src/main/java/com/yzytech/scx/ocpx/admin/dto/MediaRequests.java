package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class MediaRequests {

	private MediaRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Create {

		@NotBlank
		@Size(max = 128)
		private String mediaName;

		@Size(max = 128)
		private String credentialConfigKey;

		@NotBlank
		@Size(max = 255)
		private String uri;

		@Size(max = 4000)
		private String macroParams;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Update {

		@NotBlank
		@Size(max = 128)
		private String mediaName;

		@Size(max = 128)
		private String credentialConfigKey;

		@NotBlank
		@Size(max = 255)
		private String uri;

		@Size(max = 4000)
		private String macroParams;

	}

}
