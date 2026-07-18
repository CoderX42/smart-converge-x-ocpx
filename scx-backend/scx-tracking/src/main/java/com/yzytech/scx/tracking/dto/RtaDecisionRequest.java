package com.yzytech.scx.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

@Data
public class RtaDecisionRequest {

	@NotBlank
	private String schemaVersion;

	@NotBlank
	private String requestId;

	@NotBlank
	private String taskCode;

	@NotBlank
	private String deviceKeyHash;

	private Map<String, Object> attributes;

}
