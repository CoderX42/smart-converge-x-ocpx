package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CreateAgreementInstanceRequest(@NotBlank String templateId, @NotBlank String organizationId,
		LocalDateTime expiredAt) {
}
