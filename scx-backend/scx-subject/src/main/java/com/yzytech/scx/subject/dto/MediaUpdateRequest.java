package com.yzytech.scx.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MediaUpdateRequest {

	@NotBlank
	@Size(max = 128)
	private String name;

	@NotBlank
	@Size(max = 64)
	private String timezone;

	@NotBlank
	@Pattern(regexp = "^CNY$")
	private String settlementCurrency;

	@NotNull
	private Integer version;

}
