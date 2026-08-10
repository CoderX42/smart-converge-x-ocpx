package com.yzytech.scx.ocpx.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CanonicalRouteEventSnapshotTest {

	@Test
	void validatesAnImmutableCanonicalSnapshot() {
		String fingerprint = fingerprint();
		var snapshot = snapshot(fingerprint, "external-code");

		assertEquals(2, snapshot.snapshotSchemaVersion());
		assertEquals("CANONICAL", snapshot.snapshotMode());
		assertEquals(fingerprint, snapshot.snapshotFingerprint());
	}

	@Test
	void rejectsTamperingAndUnknownExternalCodeDoesNotMatchByGuessing() {
		assertThrows(IllegalArgumentException.class, () -> snapshot(fingerprint(), "different-external-code"));
	}

	private CanonicalRouteEventSnapshot snapshot(String fingerprint, String externalCode) {
		return new CanonicalRouteEventSnapshot(2, "CANONICAL", "task-1", 7, "binding-1", 3, "definition-1", 5,
				"mapping-1", 4, "REGISTER", "DOWNSTREAM_DELIVERY", externalCode, "STANDARD_EVENT_V1", 9000, 100L,
				"NATURAL_DAY", "Asia/Shanghai", false, "SHA256_MOD_10000_V1", fingerprint);
	}

	private String fingerprint() {
		return CanonicalRouteEventSnapshot.fingerprint("task-1", 7, "binding-1", 3, "definition-1", 5, "mapping-1", 4,
				"REGISTER", "external-code", 9000, 100L, false);
	}

}
