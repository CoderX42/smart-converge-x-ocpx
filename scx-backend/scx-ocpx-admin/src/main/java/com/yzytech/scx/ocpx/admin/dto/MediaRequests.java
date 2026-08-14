package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MediaRequests {

	private MediaRequests() {
	}

	public record Create(@NotBlank @Size(max = 128) String mediaName, @Size(max = 128) String credentialConfigKey,
			@NotBlank @Size(max = 255) String uri, @Size(max = 4000) String macroParams) {
	}

	public record Update(@NotBlank @Size(max = 128) String mediaName, @Size(max = 128) String credentialConfigKey,
			@NotBlank @Size(max = 255) String uri, @Size(max = 4000) String macroParams) {
	}

}
