package com.yzytech.scx.subject.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MediaStatusRequest {

	@NotBlank
	@Pattern(regexp = "^(ACTIVE|DISABLED)$")
	private String status;

	@NotNull
	private Integer version;

}
