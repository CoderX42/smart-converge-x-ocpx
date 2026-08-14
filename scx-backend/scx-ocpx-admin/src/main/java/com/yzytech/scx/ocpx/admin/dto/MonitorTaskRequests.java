package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public final class MonitorTaskRequests {

	private MonitorTaskRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Create {

		@NotBlank
		@Size(max = 128)
		private String monitorName;

		@NotBlank
		private String advertiserId;

		@NotBlank
		private String promotionLinkId;

		@NotBlank
		private String mediaId;

		@NotNull
		@PositiveOrZero
		private BigDecimal unitPrice;

		@Size(max = 64)
		private String bizId;

		@Size(max = 255)
		private String remark;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Update {

		@NotBlank
		@Size(max = 128)
		private String monitorName;

		@NotBlank
		private String advertiserId;

		@NotBlank
		private String promotionLinkId;

		@NotBlank
		private String mediaId;

		@NotNull
		@PositiveOrZero
		private BigDecimal unitPrice;

		@Size(max = 64)
		private String bizId;

		@Size(max = 255)
		private String remark;

	}

}
