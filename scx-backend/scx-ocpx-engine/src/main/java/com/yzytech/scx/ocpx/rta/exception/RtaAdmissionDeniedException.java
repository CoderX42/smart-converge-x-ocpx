package com.yzytech.scx.ocpx.rta.exception;

/** RTA 任务点击未取得有效 admission 时阻断现有 Feedback 链路。 */
public class RtaAdmissionDeniedException extends RuntimeException {

	private final String reasonCode;

	public RtaAdmissionDeniedException(String reasonCode) {
		super(reasonCode);
		this.reasonCode = reasonCode;
	}

	public String getReasonCode() {
		return reasonCode;
	}

}
