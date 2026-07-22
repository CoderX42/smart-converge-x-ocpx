package com.yzytech.scx.tracking.service;

import java.util.List;

public interface FeedbackBuffer {

	EnqueueResult enqueue(AcceptedEventCommand command);

	List<BufferedFeedback> poll(int limit);

	void acknowledge(BufferedFeedback feedback);

	void release(BufferedFeedback feedback);

	void deadLetter(BufferedFeedback feedback, String errorCode);

	record EnqueueResult(Status status, String receiptId) {
	}

	enum Status {

		ACCEPTED, DUPLICATE, CONFLICT

	}

	record BufferedFeedback(String bufferId, AcceptedEventCommand command, long deliveryCount) {
	}

}
