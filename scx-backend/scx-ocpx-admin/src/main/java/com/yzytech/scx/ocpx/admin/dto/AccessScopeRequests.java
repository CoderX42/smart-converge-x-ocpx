package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AccessScopeRequests {

	private AccessScopeRequests() {
	}

	public record Replace(@NotNull Long version, @NotNull @Size(max = 500) List<@Valid Scope> scopes) {
	}

	public record Scope(@NotBlank String scopeType, String scopeId, @NotBlank String permissionLevel,
			LocalDateTime validFrom, LocalDateTime validTo) {
	}

}
