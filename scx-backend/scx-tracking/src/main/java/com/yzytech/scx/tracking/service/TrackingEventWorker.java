package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.RtaDecisionLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackingEventWorker {

	private static final Logger log = LoggerFactory.getLogger(TrackingEventWorker.class);

	private final TrackingProperties properties;

	private final FeedbackBuffer feedbackBuffer;

	private final AcceptedEventPersistenceService persistenceService;

	private final WorkLeaseService leaseService;

	private final EventProcessingService processingService;

	private final RtaDecisionLogBuffer rtaDecisionLogBuffer;

	private final RtaDecisionLogMapper rtaDecisionLogMapper;

	@Scheduled(fixedDelayString = "${scx.tracking.feedback-consume-delay-ms:100}")
	public void consumeFeedback() {
		if (!properties.isWorkerEnabled())
			return;
		List<FeedbackBuffer.BufferedFeedback> batch = feedbackBuffer.poll(properties.getFeedbackBatchSize());
		for (FeedbackBuffer.BufferedFeedback feedback : batch) {
			try {
				persistenceService.persist(feedback.command());
				feedbackBuffer.acknowledge(feedback);
			}
			catch (EventConflictException exception) {
				feedbackBuffer.deadLetter(feedback, "EVENT_PAYLOAD_CONFLICT");
			}
			catch (RuntimeException exception) {
				if (feedback.deliveryCount() >= properties.getFeedbackMaxDeliveries())
					feedbackBuffer.deadLetter(feedback, "PERSISTENCE_RETRY_EXHAUSTED");
				else
					feedbackBuffer.release(feedback);
				log.warn("Feedback persistence failed; record remains retryable: {}",
						exception.getClass().getSimpleName());
			}
		}
	}

	@Scheduled(fixedDelayString = "${scx.tracking.event-process-delay-ms:100}")
	public void processAcceptedEvents() {
		if (!properties.isWorkerEnabled())
			return;
		for (RawEvent raw : leaseService.claimRawEvents(properties.getWorkerBatchSize())) {
			try {
				processingService.process(raw.getId());
			}
			catch (RuntimeException exception) {
				log.warn("Accepted event processing failed; record remains retryable: {}",
						exception.getClass().getSimpleName());
			}
		}
	}

	@Scheduled(fixedDelayString = "${scx.tracking.rta-log-consume-delay-ms:100}")
	public void persistRtaDecisionLogs() {
		if (!properties.isWorkerEnabled())
			return;
		for (RtaDecisionLogBuffer.BufferedLog buffered : rtaDecisionLogBuffer.poll(properties.getWorkerBatchSize())) {
			try {
				rtaDecisionLogMapper.insert(buffered.log());
				rtaDecisionLogBuffer.acknowledge(buffered);
			}
			catch (org.springframework.dao.DuplicateKeyException exception) {
				rtaDecisionLogBuffer.acknowledge(buffered);
			}
			catch (RuntimeException exception) {
				if (buffered.deliveryCount() >= properties.getFeedbackMaxDeliveries())
					rtaDecisionLogBuffer.deadLetter(buffered, "PERSISTENCE_RETRY_EXHAUSTED");
				log.warn("RTA decision-log persistence failed; record remains retryable: {}",
						exception.getClass().getSimpleName());
			}
		}
	}

}
