package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAdvertiserRequest(@NotBlank String advertiserName, String industry, String websiteUrl) {
}
