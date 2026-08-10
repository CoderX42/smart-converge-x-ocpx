package com.yzytech.scx.ocpx.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * S6 immutable configuration contract. Persistence of this value belongs to the S7
 * publish transaction; constructing or validating it has no external side effects.
 */
public record CanonicalRouteEventSnapshot(int snapshotSchemaVersion, String snapshotMode, String taskId,
		long taskConfigRevision, String taskEventBindingId, long taskEventBindingRevision, String eventDefinitionId,
		long eventDefinitionRevision, String eventMappingId, long eventMappingRevision, String standardEventCode,
		String direction, String externalEventCode, String parameterTemplateCode, int callbackRatioBasisPoints,
		Long limitValue, String limitWindow, String limitTimezone, boolean overLimitDelivery, String samplingAlgorithm,
		String snapshotFingerprint) {

	public static final int SCHEMA_VERSION = 2;

	public static final String MODE = "CANONICAL";

	public static final String WINDOW = "NATURAL_DAY";

	public static final String TIMEZONE = "Asia/Shanghai";

	public static final String ALGORITHM = "SHA256_MOD_10000_V1";

	public CanonicalRouteEventSnapshot {
		require(snapshotSchemaVersion == SCHEMA_VERSION, "canonical event snapshot schemaVersion must be 2");
		require(MODE.equals(snapshotMode), "canonical event snapshot mode is required");
		requireText(taskId, "taskId");
		require(taskConfigRevision >= 1, "taskConfigRevision must be positive");
		requireText(taskEventBindingId, "taskEventBindingId");
		require(taskEventBindingRevision >= 1, "taskEventBindingRevision must be positive");
		requireText(eventDefinitionId, "eventDefinitionId");
		require(eventDefinitionRevision >= 1, "eventDefinitionRevision must be positive");
		requireText(eventMappingId, "eventMappingId");
		require(eventMappingRevision >= 1, "eventMappingRevision must be positive");
		requireText(standardEventCode, "standardEventCode");
		require("DOWNSTREAM_DELIVERY".equals(direction), "direction must be DOWNSTREAM_DELIVERY");
		requireText(externalEventCode, "externalEventCode");
		require(List.of("STANDARD_EVENT_V1", "FLAT_EVENT_V1").contains(parameterTemplateCode),
				"parameterTemplateCode is not controlled");
		require(callbackRatioBasisPoints >= 0 && callbackRatioBasisPoints <= 10000,
				"callbackRatioBasisPoints must be between 0 and 10000");
		require(limitValue == null || limitValue >= 0, "limitValue must be non-negative or null");
		require(WINDOW.equals(limitWindow), "limitWindow must be NATURAL_DAY");
		require(TIMEZONE.equals(limitTimezone), "limitTimezone must be Asia/Shanghai");
		require(ALGORITHM.equals(samplingAlgorithm), "samplingAlgorithm must be SHA256_MOD_10000_V1");
		requireText(snapshotFingerprint, "snapshotFingerprint");
		String expected = fingerprint(taskId, taskConfigRevision, taskEventBindingId, taskEventBindingRevision,
				eventDefinitionId, eventDefinitionRevision, eventMappingId, eventMappingRevision, standardEventCode,
				externalEventCode, callbackRatioBasisPoints, limitValue, overLimitDelivery);
		require(expected.equals(snapshotFingerprint), "snapshotFingerprint does not match immutable fields");
	}

	public static String fingerprint(String taskId, long taskConfigRevision, String taskEventBindingId,
			long taskEventBindingRevision, String eventDefinitionId, long eventDefinitionRevision,
			String eventMappingId, long eventMappingRevision, String standardEventCode, String externalEventCode,
			int ratioBasisPoints, Long limitValue, boolean overLimitDelivery) {
		String value = String.join("|", taskId, String.valueOf(taskConfigRevision), taskEventBindingId,
				String.valueOf(taskEventBindingRevision), eventDefinitionId, String.valueOf(eventDefinitionRevision),
				eventMappingId, String.valueOf(eventMappingRevision), standardEventCode, externalEventCode,
				String.valueOf(ratioBasisPoints), limitValue == null ? "UNLIMITED" : limitValue.toString(),
				String.valueOf(overLimitDelivery), ALGORITHM);
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("cannot calculate event snapshot fingerprint", error);
		}
	}

	private static void requireText(String value, String field) {
		require(value != null && !value.isBlank(), field + " is required");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
