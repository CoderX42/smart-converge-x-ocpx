package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgreementTemplateRequest(@NotBlank String templateType, @NotBlank String name,
		@NotBlank String content) {
}
