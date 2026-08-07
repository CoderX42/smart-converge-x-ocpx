package com.yzytech.scx.ocpx.runtime.model;

public record DeliveryAttemptAudit(String requestMethod, String targetHost, String targetPath, String requestSummary,
		String responseExcerpt, Long durationMs) {

	public static DeliveryAttemptAudit empty() {
		return new DeliveryAttemptAudit(null, null, null, null, null, null);
	}

}
