package com.yzytech.scx.subject.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MediaScopeGrantRequest {

	@NotBlank
	private String userId;

	@NotBlank
	private String mediaId;

}
