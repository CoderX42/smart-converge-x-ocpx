package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateQualificationCaseRequest(@NotBlank String caseType) {
}
