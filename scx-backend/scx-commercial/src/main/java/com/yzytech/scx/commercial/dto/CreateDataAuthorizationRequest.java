package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateDataAuthorizationRequest(@NotBlank String grantorOrganizationId,
		@NotBlank String granteeOrganizationId, @NotBlank String authorizationType, String scope, LocalDate startDate,
		LocalDate endDate) {
}
