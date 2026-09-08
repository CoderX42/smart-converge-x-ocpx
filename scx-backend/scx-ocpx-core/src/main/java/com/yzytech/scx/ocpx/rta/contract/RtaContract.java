package com.yzytech.scx.ocpx.rta.contract;

import java.util.List;
import java.util.Map;

/** Shared wire messages only; no Spring runtime components in Core. */
public final class RtaContract {

	private RtaContract() {
	}

	public static final String PREFIX = "scx:{rta-async}:";

	public static final long RETENTION_MS = 86_400_000L;

	public record Request(String requestId, List<String> taskIds, Map<String, String> device,
			Map<String, String> context) {
	}

	public record Candidate(String productId, String taskId) {
	}

	public record Decision(String relayRequestId, String status, List<Candidate> matched) {
	}

	public record Accepted(String requestId, String relayRequestId, long deadline) {
	}

	public record Notification(String requestId, String relayRequestId, String status, List<String> matchedTaskIds,
			String reasonCode, long expiresAt) {
	}

	public record Binding(String taskId, String advertiserId, String mediaId, String linkId, String upstreamId,
			String productId, String externalTaskId, long version) {
	}

	public record Upstream(String id, String advertiserId, String accessCode, String endpoint, String credentialRef,
			long version, int timeoutMs, int admissionSeconds, boolean enabled, String protocol,
			int configSchemaVersion, Map<String, String> protocolConfig) {

		public Upstream {
			protocol = protocol == null ? "GENERIC_ASYNC" : protocol;
			configSchemaVersion = configSchemaVersion == 0 ? 1 : configSchemaVersion;
			protocolConfig = protocolConfig == null ? Map.of() : Map.copyOf(protocolConfig);
		}

		public Upstream(String id, String advertiserId, String accessCode, String endpoint, String credentialRef,
				long version, int timeoutMs, int admissionSeconds, boolean enabled) {
			this(id, advertiserId, accessCode, endpoint, credentialRef, version, timeoutMs, admissionSeconds, enabled,
					"GENERIC_ASYNC", 1, Map.of());
		}
	}

	public record Media(String mediaId, String notifyUrl, String credentialRef, long version, boolean enabled) {
	}

	public record Log(String id, String relayRequestId, String requestId, String mediaId, String advertiserId,
			String upstreamId, String taskId, String stage, String status, String reason, long durationMs,
			long eventTime) {
	}

}
