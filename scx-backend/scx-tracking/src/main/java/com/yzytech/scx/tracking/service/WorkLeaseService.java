package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.EventOutboxMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkLeaseService {

	private final CallbackJobMapper callbackMapper;

	private final EventOutboxMapper outboxMapper;

	private final RawEventMapper rawEventMapper;

	private final TrackingProperties properties;

	private final String owner = "worker-" + UUID.randomUUID();

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<CallbackJob> claimCallbacks(int limit) {
		List<String> ids = callbackMapper.selectClaimableIdsForUpdate(limit);
		if (ids.isEmpty())
			return List.of();
		callbackMapper.claim(ids, owner, leaseUntil());
		return callbackMapper.selectOwned(ids, owner);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<EventOutbox> claimOutbox(int limit) {
		List<String> ids = outboxMapper.selectClaimableIdsForUpdate(limit);
		if (ids.isEmpty())
			return List.of();
		outboxMapper.claim(ids, owner, leaseUntil());
		return outboxMapper.selectOwned(ids, owner);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<RawEvent> claimRawEvents(int limit) {
		List<String> ids = rawEventMapper.selectClaimableIdsForUpdate(limit);
		if (ids.isEmpty())
			return List.of();
		rawEventMapper.claim(ids, owner, leaseUntil());
		return rawEventMapper.selectOwned(ids, owner);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean completeCallback(CallbackJob job) {
		return callbackMapper.completeOwned(job, owner) == 1;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean completeOutbox(EventOutbox value) {
		return outboxMapper.completeOwned(value, owner) == 1;
	}

	String owner() {
		return owner;
	}

	private LocalDateTime leaseUntil() {
		return LocalDateTime.now().plusSeconds(properties.getWorkerLeaseSeconds());
	}

}
