package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.config.TrackingProperties;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scx.tracking", name = "feedback-store", havingValue = "memory", matchIfMissing = true)
public class MemoryFeedbackBuffer implements FeedbackBuffer {

	private final TrackingProperties properties;

	private final Map<String, IdempotencyValue> idempotency = new ConcurrentHashMap<>();

	private ArrayBlockingQueue<BufferedFeedback> queue;

	public MemoryFeedbackBuffer(TrackingProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void initialize() {
		queue = new ArrayBlockingQueue<>(properties.getFeedbackQueueCapacity());
	}

	@Override
	public EnqueueResult enqueue(AcceptedEventCommand command) {
		String key = command.clientId() + ":" + command.eventId();
		IdempotencyValue newValue = new IdempotencyValue(command.payloadHash(), command.receiptId());
		IdempotencyValue existing = idempotency.putIfAbsent(key, newValue);
		if (existing != null) {
			return new EnqueueResult(
					existing.payloadHash.equals(command.payloadHash()) ? Status.DUPLICATE : Status.CONFLICT,
					existing.receiptId);
		}
		if (!queue.offer(new BufferedFeedback(command.receiptId(), command, 1))) {
			idempotency.remove(key, newValue);
			throw new FeedbackStorageException("Feedback 内存队列已满");
		}
		return new EnqueueResult(Status.ACCEPTED, command.receiptId());
	}

	@Override
	public List<BufferedFeedback> poll(int limit) {
		List<BufferedFeedback> values = new ArrayList<>(limit);
		queue.drainTo(values, limit);
		return values;
	}

	@Override
	public void acknowledge(BufferedFeedback feedback) {
		// The in-memory implementation is intentionally local-only and has no durable
		// ack.
	}

	@Override
	public void release(BufferedFeedback feedback) {
		if (!queue.offer(new BufferedFeedback(feedback.bufferId(), feedback.command(), feedback.deliveryCount() + 1)))
			throw new FeedbackStorageException("Feedback 内存队列重试失败");
	}

	@Override
	public void deadLetter(BufferedFeedback feedback, String errorCode) {
		// Local-only mode surfaces the processing error through metrics/logging in the
		// worker.
	}

	private record IdempotencyValue(String payloadHash, String receiptId) {
	}

}
