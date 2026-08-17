package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
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
		private Boolean billingEnabled = false;

		@NotNull
		@Min(1)
		@Max(2)
		private Integer billingType = 1;

		@NotNull
		@PositiveOrZero
		@Digits(integer = 14, fraction = 6)
		private BigDecimal unitPrice;

		@NotBlank
		@Pattern(regexp = "ACTIVATE|EVOKE|LOGIN|REGISTER|PAYMENT|CUSTOMER_ACQUISITION|NEXT_STAY|VISITS|AUTHENTICATION|ORDER|FINISH_THE_ORDER|FIRST_ACTIVATION|FIRST_ORDER")
		private String conversionEvent = "ACTIVATE";

		@Size(max = 64)
		private String bizId;

		@Size(max = 255)
		private String remark;

		public Create(String monitorName, String advertiserId, String promotionLinkId, String mediaId,
				BigDecimal unitPrice, String bizId, String remark) {
			this(monitorName, advertiserId, promotionLinkId, mediaId, false, 1, unitPrice, "ACTIVATE", bizId,
					remark);
		}

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

		private Boolean billingEnabled;

		@Min(1)
		@Max(2)
		private Integer billingType;

		@NotNull
		@PositiveOrZero
		@Digits(integer = 14, fraction = 6)
		private BigDecimal unitPrice;

		@NotBlank
		@Pattern(regexp = "ACTIVATE|EVOKE|LOGIN|REGISTER|PAYMENT|CUSTOMER_ACQUISITION|NEXT_STAY|VISITS|AUTHENTICATION|ORDER|FINISH_THE_ORDER|FIRST_ACTIVATION|FIRST_ORDER")
		private String conversionEvent = "ACTIVATE";

		@Size(max = 64)
		private String bizId;

		@Size(max = 255)
		private String remark;

		public Update(String monitorName, String advertiserId, String promotionLinkId, String mediaId,
				BigDecimal unitPrice, String bizId, String remark) {
			this(monitorName, advertiserId, promotionLinkId, mediaId, null, null, unitPrice, "ACTIVATE", bizId,
					remark);
		}

		public Update(String monitorName, String advertiserId, String promotionLinkId, String mediaId,
				BigDecimal unitPrice, String conversionEvent, String bizId, String remark) {
			this(monitorName, advertiserId, promotionLinkId, mediaId, null, null, unitPrice, conversionEvent, bizId,
					remark);
		}

	}

}
