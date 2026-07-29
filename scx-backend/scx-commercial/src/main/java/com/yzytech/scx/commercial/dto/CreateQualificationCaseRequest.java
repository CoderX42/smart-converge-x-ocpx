package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateQualificationCaseRequest(@NotBlank String orgId, @NotBlank String caseType) {
}
