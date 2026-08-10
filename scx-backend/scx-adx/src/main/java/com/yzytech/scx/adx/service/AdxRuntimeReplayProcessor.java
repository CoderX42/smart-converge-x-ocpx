package com.yzytech.scx.adx.service;

/**
 * Controlled S10 processor boundary. The production default is unavailable; tests may
 * inject a fake implementation without network clients.
 */
public interface AdxRuntimeReplayProcessor {

	boolean available();

	Result process(Context context);

	record Context(String commandId, String failureId, String routeVersionId, long routeVersion,
			int eventSnapshotSchemaVersion, String idempotencyKey) {
	}

	record Result(boolean success, boolean retryable, String errorClass, String errorCode,
			long forwardedConversionCount) {

		public static Result succeeded(long forwardedConversionCount) {
			return new Result(true, false, null, null, Math.max(0, forwardedConversionCount));
		}

		public static Result retry(String errorClass, String errorCode) {
			return new Result(false, true, errorClass, errorCode, 0);
		}

		public static Result terminal(String errorClass, String errorCode) {
			return new Result(false, false, errorClass, errorCode, 0);
		}
	}

}
