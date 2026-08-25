package com.yzytech.scx.ocpx.audit.store;

import com.yzytech.scx.ocpx.audit.config.WireAuditProperties;
import com.yzytech.scx.ocpx.audit.model.WireAuditRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WireAuditStore {

	private final WireAuditProperties properties;
	private final EncryptedWireAuditWal wal;
	private final WireAuditRepository repository;
	private final Counter captured;
	private final Counter completed;
	private final Counter persisted;
	private final Counter replayed;
	private final Counter gaps;

	public WireAuditStore(WireAuditProperties properties, EncryptedWireAuditWal wal, WireAuditRepository repository,
			MeterRegistry meterRegistry) {
		this.properties = properties;
		this.wal = wal;
		this.repository = repository;
		this.captured = meterRegistry.counter("scx.ocpx.audit.captured");
		this.completed = meterRegistry.counter("scx.ocpx.audit.completed");
		this.persisted = meterRegistry.counter("scx.ocpx.audit.persisted");
		this.replayed = meterRegistry.counter("scx.ocpx.audit.replayed");
		this.gaps = meterRegistry.counter("scx.ocpx.audit.coverage.gaps");
		Gauge.builder("scx.ocpx.audit.wal.backlog.bytes", wal, EncryptedWireAuditWal::backlogBytes)
			.register(meterRegistry);
		Gauge.builder("scx.ocpx.audit.wal.oldest.age.seconds", wal, EncryptedWireAuditWal::oldestBacklogAgeSeconds)
			.register(meterRegistry);
	}

	public void persist(WireAuditRecord record) {
		if (!properties.isEnabled())
			return;
		if ("PENDING".equals(record.getResultStatus()))
			captured.increment();
		else
			completed.increment();
		boolean durableWal = wal.append(record);
		try {
			repository.upsert(record);
			persisted.increment();
		}
		catch (RuntimeException databaseError) {
			if (!durableWal) {
				gaps.increment();
				log.error("OCPX_AUDIT_DUAL_WRITE_FAILURE auditId:{} stage:{} coverageGapCount:{}", record.getId(),
						record.getStageCode(), gaps.count(), databaseError);
			}
			else {
				log.error("OCPX_AUDIT_DATABASE_WRITE_DEFERRED auditId:{} stage:{}", record.getId(),
						record.getStageCode());
			}
		}
	}

	public void replay() {
		replayed.increment(wal.rotateAndReplay(repository));
	}

}
