package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.entity.RtaDecisionLog;
import java.util.List;

public interface RtaDecisionLogBuffer {

	Reservation reserve(String taskId, String requestId);

	void complete(String taskId, String requestId, Result result, RtaDecisionLog log);

	List<BufferedLog> poll(int limit);

	void acknowledge(BufferedLog log);

	void deadLetter(BufferedLog log, String errorCode);

	record Result(String decision, String reasonCode) {
	}

	record Reservation(Status status, Result result) {
	}

	enum Status {

		NEW, PENDING, COMPLETED

	}

	record BufferedLog(String recordId, RtaDecisionLog log, long deliveryCount) {
	}

}
