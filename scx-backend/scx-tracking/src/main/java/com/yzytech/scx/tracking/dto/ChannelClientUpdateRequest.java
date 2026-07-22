package com.yzytech.scx.tracking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ChannelClientUpdateRequest {

	@NotBlank
	private String mediaId;

	@Min(1)
	@Max(20_000)
	private Integer maxQps;

	private Boolean dynamicCallbackEnabled;

	@Size(max = 20)
	private List<@Size(max = 255) String> callbackAllowedHosts = List.of();

	@NotBlank
	private String status;

}
