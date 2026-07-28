package com.yzytech.scx.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MediaCreateRequest {

	@NotBlank
	@Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,63}$")
	private String code;

	@NotBlank
	@Size(max = 128)
	private String name;

	@Size(max = 64)
	private String timezone = "Asia/Shanghai";

	@Pattern(regexp = "^CNY$")
	private String settlementCurrency = "CNY";

}
