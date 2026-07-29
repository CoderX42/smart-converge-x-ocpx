package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateAgencyRelationRequest(@NotBlank String parentAgencyOrgId, @NotBlank String childOrgId,
		String relationType, String authorizedScopes, LocalDate startDate, LocalDate endDate) {
}
