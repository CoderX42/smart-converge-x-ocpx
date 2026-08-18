package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public final class ProtocolConfigRequests {

	private ProtocolConfigRequests() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class UpdateAllowedHosts {

		@NotNull
		@Size(max = 50)
		private List<@Size(max = 253) String> allowedHosts = new ArrayList<>();

	}

}
