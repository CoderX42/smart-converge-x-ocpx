package com.yzytech.scx.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceIdentifier {

	@NotBlank
	private String platform;

	@NotBlank
	private String idType;

	@NotBlank
	private String hashAlgorithm;

	@NotBlank
	private String idHash;

	private String idVersion;

}
