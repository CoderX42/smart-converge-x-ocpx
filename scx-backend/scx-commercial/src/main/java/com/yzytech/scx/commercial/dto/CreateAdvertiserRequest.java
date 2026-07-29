package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAdvertiserRequest(@NotBlank String orgName, @NotBlank String orgType,
		@NotBlank String advertiserName, String contactName, String contactPhone, String contactEmail, String industry,
		String websiteUrl) {
}
