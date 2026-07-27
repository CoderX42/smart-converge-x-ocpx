package com.yzytech.scx.tracking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ChannelClientCreateRequest {

	@NotBlank
	private String mediaId;

	@NotBlank
	@Size(max = 64)
	private String sourceCode;

	@Min(1)
	@Max(20_000)
	private Integer maxQps = 2_000;

	@Size(min = 1, max = 3)
	private List<@Size(max = 16) String> allowedScopes = List.of("CLICK", "CONVERSION", "RTA");

	private Boolean dynamicCallbackEnabled = false;

	@Size(max = 20)
	private List<@Size(max = 255) String> callbackAllowedHosts = List.of();

}
