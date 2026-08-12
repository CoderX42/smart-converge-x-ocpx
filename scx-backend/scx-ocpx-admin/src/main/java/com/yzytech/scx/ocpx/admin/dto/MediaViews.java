package com.yzytech.scx.ocpx.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MediaViews {

	private MediaViews() {
	}

	public record Item(String id, String mediaCode, String mediaName, String adapterCode, String authType,
			String credentialConfigKey, Long credentialVersion, String ingressUri, List<String> macroParams,
			String callbackUrl, List<String> callbackHostAllowlist, String status, long configVersion,
			LocalDateTime createTime, LocalDateTime updateTime) {
	}

	public record Mutation(String id, String status, long configVersion) {
	}

	public record Preflight(String status, boolean adapterSupported, boolean callbackUrlValid,
			boolean credentialReferenceComplete, List<String> issues) {
	}

	public record Connectivity(String status, boolean networkCalled, String message) {
	}

}
