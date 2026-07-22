package com.yzytech.scx.tracking.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.mapper.RtaDecisionLogMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackingEventWorkerTest {

	@Test
	void fifthPersistenceFailureMovesFeedbackToDlq() {
		TrackingProperties properties = new TrackingProperties();
		properties.setWorkerEnabled(true);
		properties.setFeedbackMaxDeliveries(5);
		FeedbackBuffer buffer = mock(FeedbackBuffer.class);
		AcceptedEventPersistenceService persistence = mock(AcceptedEventPersistenceService.class);
		AcceptedEventCommand command = new AcceptedEventCommand("receipt-1", "client-1", "PARTNER", "event-1", "TASK-1",
				"CLICK", OffsetDateTime.now(), OffsetDateTime.now(), "ANDROID", "OAID", null, "v1", "a".repeat(64),
				null, "b".repeat(64), "{}", null, null, null, null, null);
		FeedbackBuffer.BufferedFeedback feedback = new FeedbackBuffer.BufferedFeedback("stream|1-0", command, 5);
		when(buffer.poll(properties.getFeedbackBatchSize())).thenReturn(List.of(feedback));
		org.mockito.Mockito.doThrow(new IllegalStateException("database down")).when(persistence).persist(command);
		TrackingEventWorker worker = new TrackingEventWorker(properties, buffer, persistence,
				mock(WorkLeaseService.class), mock(EventProcessingService.class), mock(RtaDecisionLogBuffer.class),
				mock(RtaDecisionLogMapper.class));

		worker.consumeFeedback();

		verify(buffer).deadLetter(feedback, "PERSISTENCE_RETRY_EXHAUSTED");
	}

}
