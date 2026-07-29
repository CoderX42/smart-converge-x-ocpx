package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;

public record OrganizationScopeGrantRequest(@NotBlank String userId, @NotBlank String organizationId) {
}
