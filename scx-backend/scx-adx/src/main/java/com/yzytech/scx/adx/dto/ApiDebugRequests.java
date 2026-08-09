package com.yzytech.scx.adx.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiDebugRequests {

	private ApiDebugRequests() {
	}

	public record Preflight(@NotBlank @Pattern(regexp = "CUSTOM|INTEGRATED") String mode,
			@Pattern(regexp = "DOWNSTREAM_TO_UPSTREAM|UPSTREAM_TO_DOWNSTREAM") String scenario,
			@Size(max = 32) String monitorId,
			@NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
			@NotBlank @Size(max = 5000) String url,
			@Size(max = 50) Map<@Size(max = 200) String, @Size(max = 8192) String> headers,
			@NotBlank @Pattern(regexp = "NONE|JSON|FORM_URLENCODED|FORM_DATA|TEXT") String bodyType,
			@Size(max = 524288) String body, @Min(1) @Max(10) Integer timeoutSeconds) {

		public Map<String, String> safeHeaders() {
			return headers == null ? Map.of() : new LinkedHashMap<>(headers);
		}

		public int effectiveTimeoutSeconds() {
			return timeoutSeconds == null ? 5 : timeoutSeconds;
		}
	}

	public record Authorize(@NotBlank @Size(max = 32) String draftId, @NotBlank @Size(max = 200) String password,
			@NotBlank @Size(max = 200) String confirmationPhrase,
			@NotBlank @Pattern(regexp = "EXECUTE|RAW_LOGS") String purpose) {
	}

	public record Execute(@NotBlank @Size(max = 32) String draftId,
			@NotBlank @Size(max = 128) String authorizationToken) {
	}

	public record CaseSave(@Size(max = 32) String id, @NotBlank @Size(max = 120) String caseName,
			@Size(max = 500) String description, @NotBlank @Pattern(regexp = "CUSTOM|INTEGRATED") String mode,
			@Pattern(regexp = "DOWNSTREAM_TO_UPSTREAM|UPSTREAM_TO_DOWNSTREAM") String scenario,
			@Size(max = 32) String monitorId, @NotBlank @Size(max = 524288) String requestJson) {
	}

}
