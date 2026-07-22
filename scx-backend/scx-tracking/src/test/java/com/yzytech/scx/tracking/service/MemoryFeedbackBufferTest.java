package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yzytech.scx.tracking.config.TrackingProperties;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MemoryFeedbackBufferTest {

	@Test
	void enforcesTransportIdempotencyAndPayloadConflict() {
		MemoryFeedbackBuffer buffer = buffer(10);
		AcceptedEventCommand first = command("event-1", "hash-1", "receipt-1");

		assertThat(buffer.enqueue(first).status()).isEqualTo(FeedbackBuffer.Status.ACCEPTED);
		assertThat(buffer.enqueue(command("event-1", "hash-1", "receipt-2")).status())
			.isEqualTo(FeedbackBuffer.Status.DUPLICATE);
		assertThat(buffer.enqueue(command("event-1", "hash-2", "receipt-3")).status())
			.isEqualTo(FeedbackBuffer.Status.CONFLICT);
	}

	@Test
	void fullQueueFailsInsteadOfConfirmingAnUnstoredClick() {
		MemoryFeedbackBuffer buffer = buffer(1);
		buffer.enqueue(command("event-1", "hash-1", "receipt-1"));

		assertThatThrownBy(() -> buffer.enqueue(command("event-2", "hash-2", "receipt-2")))
			.isInstanceOf(FeedbackStorageException.class)
			.hasMessage("Feedback 内存队列已满");
	}

	@Test
	void releasedRecordBecomesRetryableAndIncrementsDeliveryCount() {
		MemoryFeedbackBuffer buffer = buffer(2);
		buffer.enqueue(command("event-1", "hash-1", "receipt-1"));
		FeedbackBuffer.BufferedFeedback first = buffer.poll(1).get(0);
		buffer.release(first);

		assertThat(buffer.poll(1).get(0).deliveryCount()).isEqualTo(2);
	}

	private MemoryFeedbackBuffer buffer(int capacity) {
		TrackingProperties properties = new TrackingProperties();
		properties.setFeedbackQueueCapacity(capacity);
		MemoryFeedbackBuffer buffer = new MemoryFeedbackBuffer(properties);
		buffer.initialize();
		return buffer;
	}

	private AcceptedEventCommand command(String eventId, String payloadHash, String receiptId) {
		OffsetDateTime now = OffsetDateTime.parse("2026-07-22T20:00:00+08:00");
		return new AcceptedEventCommand(receiptId, "client-1", "channel-a", eventId, "TASK-1", "CLICK", now, now,
				"ANDROID", "OAID", "", "v1", "a".repeat(64), null, payloadHash, "{}", null, null, null, null, null);
	}

}
