package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;

import java.math.BigDecimal;
import java.util.List;

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

		@NotNull
		private OcpxUpstreamProtocol upstreamProtocol;

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

		@NotNull
		private OcpxUpstreamProtocol upstreamProtocol;

		@Size(max = 5000)
		private String bidUrl;

		@Size(max = 4000)
		private String macroParams;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Recharge {

		@NotBlank
		@Size(min = 8, max = 64)
		private String requestId;

		@NotNull
		@Positive
		@Digits(integer = 14, fraction = 6)
		private BigDecimal amount;

		@Size(max = 255)
		private String remark;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Deduction {

		@NotBlank
		@Size(min = 8, max = 64)
		private String requestId;

		@NotNull
		@Positive
		@Digits(integer = 14, fraction = 6)
		private BigDecimal amount;

		@NotBlank
		@Size(max = 255)
		private String remark;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RolePermissionsUpdate {

		@NotNull
		@Size(max = 128)
		private List<@NotBlank @Size(max = 64) String> menuIds;

		@NotNull
		private Boolean allDataScope;

	}

}
