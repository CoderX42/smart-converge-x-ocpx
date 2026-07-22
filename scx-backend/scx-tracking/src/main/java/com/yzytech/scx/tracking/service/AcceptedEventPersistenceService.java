package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.tracking.entity.ClickCallbackTarget;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.ClickCallbackTargetMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AcceptedEventPersistenceService {

	private final RawEventMapper rawEventMapper;

	private final EventOutboxMapper outboxMapper;

	private final ClickCallbackTargetMapper callbackTargetMapper;

	@Transactional
	public PersistenceResult persist(AcceptedEventCommand command) {
		RawEvent existing = findExisting(command);
		if (existing != null)
			return duplicate(existing, command.payloadHash());

		RawEvent raw = new RawEvent();
		raw.setSource(command.sourceCode());
		raw.setClientId(command.clientId());
		raw.setEventId(command.eventId());
		raw.setReceiptId(command.receiptId());
		raw.setTaskId(resolveTaskId(command.taskCode()));
		raw.setEventType(command.eventType());
		raw.setBusinessEventKeyHash(command.businessEventKeyHash());
		raw.setOccurredAt(command.occurredAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
		raw.setReceivedAt(command.receivedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
		raw.setDevicePlatform(command.devicePlatform());
		raw.setDeviceIdType(command.deviceIdType());
		raw.setDeviceIdVersion(command.deviceIdVersion());
		raw.setDeviceKeyVersion(command.deviceKeyVersion());
		raw.setDeviceKeyHash(command.deviceKeyHash());
		raw.setPayloadHash(command.payloadHash());
		raw.setPayloadJson(command.payloadJson());
		raw.setStatus("ACCEPTED");
		raw.setProcessingStatus("PENDING");
		try {
			rawEventMapper.insert(raw);
		}
		catch (DuplicateKeyException exception) {
			existing = findExisting(command);
			if (existing == null)
				throw exception;
			return duplicate(existing, command.payloadHash());
		}

		EventOutbox outbox = new EventOutbox();
		outbox.setAggregateId(raw.getId());
		outbox.setEventType("RAW_EVENT_ACCEPTED");
		outbox.setPayloadJson(command.payloadJson());
		outbox.setStatus("PENDING");
		outbox.setAttempts(0);
		outboxMapper.insert(outbox);

		if ("CLICK".equals(command.eventType()) && (StringUtils.hasText(command.callbackRef())
				|| StringUtils.hasText(command.callbackUrlCiphertext()))) {
			ClickCallbackTarget target = new ClickCallbackTarget();
			target.setClickEventId(raw.getId());
			target.setTargetRef(command.callbackRef());
			target.setEncryptedUrl(command.callbackUrlCiphertext());
			target.setUrlIv(command.callbackUrlIv());
			target.setUrlHost(command.callbackUrlHost());
			target.setUrlFingerprint(command.callbackUrlFingerprint());
			target.setKeyVersion(command.deviceKeyVersion());
			callbackTargetMapper.insert(target);
		}
		return new PersistenceResult(raw.getId(), raw.getReceiptId(), false,
				raw.getReceivedAt().atOffset(ZoneOffset.UTC));
	}

	private String resolveTaskId(String taskCode) {
		String taskId = rawEventMapper.selectTaskIdByCode(taskCode);
		if (taskId == null)
			throw new IllegalArgumentException("监测任务不可用");
		return taskId;
	}

	private RawEvent findExisting(AcceptedEventCommand command) {
		return rawEventMapper.selectOne(Wrappers.<RawEvent>lambdaQuery()
			.eq(RawEvent::getSource, command.sourceCode())
			.eq(RawEvent::getEventId, command.eventId())
			.last("LIMIT 1"));
	}

	private PersistenceResult duplicate(RawEvent existing, String payloadHash) {
		if (!payloadHash.equals(existing.getPayloadHash()))
			throw new EventConflictException("eventId 已被不同载荷占用");
		return new PersistenceResult(existing.getId(), existing.getReceiptId(), true,
				existing.getReceivedAt().atOffset(ZoneOffset.UTC));
	}

	public record PersistenceResult(String rawEventId, String receiptId, boolean duplicateDelivery,
			OffsetDateTime receivedAt) {
	}

}
