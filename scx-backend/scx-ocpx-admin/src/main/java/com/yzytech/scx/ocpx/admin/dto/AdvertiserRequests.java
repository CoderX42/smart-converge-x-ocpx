package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

public final class AdvertiserRequests {

	private AdvertiserRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Create {

		@NotBlank
		@Size(max = 100)
		private String name;

		@Size(max = 128)
		private String credentialConfigKey;

		@NotBlank
		@Size(max = 5000)
		private String bidUrl;

		@Size(max = 4000)
		private String macroParams;

		@NotBlank
		@Size(max = 100)
		private String username;

		@NotBlank
		@Size(min = 6, max = 32)
		@ToString.Exclude
		private String password;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Update {

		@NotBlank
		@Size(max = 128)
		private String name;

		@Size(max = 128)
		private String credentialConfigKey;

		@NotBlank
		@Size(max = 5000)
		private String bidUrl;

		@Size(max = 4000)
		private String macroParams;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Recharge {

		@NotNull
		@PositiveOrZero
		private BigDecimal balance;

	}

}
