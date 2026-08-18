package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public final class MonitorTaskIntegrationRequests {

	private MonitorTaskIntegrationRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Callback {

		@NotNull
		@Positive
		private Long feedbackLogId;

		@NotBlank
		@Pattern(regexp = "ACTIVATE|EVOKE|LOGIN|REGISTER|PAYMENT|CUSTOMER_ACQUISITION|NEXT_STAY|VISITS|AUTHENTICATION|ORDER|FINISH_THE_ORDER|FIRST_ACTIVATION|FIRST_ORDER")
		private String eventCode;

		@DecimalMin("0.000000")
		@Digits(integer = 20, fraction = 6)
		private BigDecimal currencyAmount;

	}

}
