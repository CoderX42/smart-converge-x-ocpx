package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class BusinessCodeRequests {

	private BusinessCodeRequests() {
	}

	public record Generate(@NotBlank @Pattern(regexp = "ADVERTISER|CHANNEL|PRODUCT|EVENT") String resourceType) {
	}

}
