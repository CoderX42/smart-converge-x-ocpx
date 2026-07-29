package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SaveOrganizationRequest(@NotBlank String name, @Pattern(
		regexp = "ENTERPRISE|BUSINESS_UNIT|DIRECT_ADVERTISER|AGENCY|AGENCY_SUB_ACCOUNT|MANAGED_ADVERTISER") String orgType,
		String parentOrgId, String legalPerson, String contactName, String contactPhone, String contactEmail) {
}
