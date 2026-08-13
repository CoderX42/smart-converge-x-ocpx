package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class AdvertiserRequests {

	private AdvertiserRequests() {
	}

	public record Create(@NotBlank @Size(max = 128) String name, @Size(max = 128) String credentialConfigKey,
			@NotBlank @Size(max = 5000) String bidUrl, @Size(max = 4000) String macroParams) {
	}

	public record Update(@NotBlank @Size(max = 128) String name, @Size(max = 128) String credentialConfigKey,
			@NotBlank @Size(max = 5000) String bidUrl, @Size(max = 4000) String macroParams) {
	}

	public record Recharge(@NotNull @PositiveOrZero BigDecimal balance) {
	}

}
