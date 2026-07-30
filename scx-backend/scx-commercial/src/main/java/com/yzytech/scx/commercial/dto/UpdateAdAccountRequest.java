package com.yzytech.scx.commercial.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAdAccountRequest(@NotBlank String accountName, String currency, String timezone,
		String responsibleOrgId, String rebateOrgId, String refundOrgId) {
}
