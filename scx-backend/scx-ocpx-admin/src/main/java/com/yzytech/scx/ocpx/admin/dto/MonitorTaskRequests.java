package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class MonitorTaskRequests {

	private MonitorTaskRequests() {
	}

	public record Create(@NotBlank @Size(max = 128) String monitorName, @NotBlank String advertiserId,
			@NotBlank String promotionLinkId, @NotBlank String mediaId,
			@NotNull @PositiveOrZero BigDecimal unitPrice, @Size(max = 64) String bizId,
			@Size(max = 255) String remark) {
	}

	public record Update(@NotBlank @Size(max = 128) String monitorName, @NotBlank String advertiserId,
			@NotBlank String promotionLinkId, @NotBlank String mediaId,
			@NotNull @PositiveOrZero BigDecimal unitPrice, @Size(max = 64) String bizId,
			@Size(max = 255) String remark) {
	}

}
