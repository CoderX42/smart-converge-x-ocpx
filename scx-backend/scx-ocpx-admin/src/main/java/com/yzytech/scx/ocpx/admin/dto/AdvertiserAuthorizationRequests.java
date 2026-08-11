package com.yzytech.scx.ocpx.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AdvertiserAuthorizationRequests {

	private AdvertiserAuthorizationRequests() {
	}

	public record Update(@Size(max = 32) List<@Size(max = 64) String> roleIds,
			@Size(max = 128) List<@Size(max = 64) String> extraMenuIds, @NotNull Long expectedGrantVersion) {
	}

}
