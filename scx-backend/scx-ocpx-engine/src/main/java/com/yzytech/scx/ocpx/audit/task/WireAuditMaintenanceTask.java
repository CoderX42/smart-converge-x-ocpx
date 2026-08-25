package com.yzytech.scx.ocpx.audit.task;

import com.yzytech.scx.ocpx.audit.config.WireAuditProperties;
import com.yzytech.scx.ocpx.audit.store.WireAuditStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class WireAuditMaintenanceTask {

	private static final int BATCH_SIZE = 1000;

	private final WireAuditProperties properties;
	private final WireAuditStore store;
	private final JdbcTemplate jdbcTemplate;
	private final MeterRegistry meterRegistry;
	private final AtomicLong lastCleanupEpochSeconds = new AtomicLong(Instant.now().getEpochSecond());

	@PostConstruct
	void registerMetrics() {
		Gauge.builder("scx.ocpx.audit.cleanup.delay.seconds", lastCleanupEpochSeconds,
				last -> Math.max(0L, Instant.now().getEpochSecond() - last.get())).register(meterRegistry);
	}

	@Scheduled(fixedDelay = 60000L, initialDelay = 30000L)
	public void replay() {
		if (!properties.isEnabled())
			return;
		store.replay();
		int interrupted = markInterrupted();
		if (interrupted > 0) {
			meterRegistry.counter("scx.ocpx.audit.interrupted").increment(interrupted);
			log.warn("wire audit interrupted requests marked count:{}", interrupted);
		}
	}

	@Scheduled(cron = "0 20 1 * * ?")
	public void clean() {
		if (!properties.isEnabled())
			return;
		int payloads = drain("DELETE FROM scx_ocpx_wire_audit_payload WHERE audit_id IN (SELECT audit_id FROM "
				+ "(SELECT p.audit_id FROM scx_ocpx_wire_audit_payload p JOIN scx_ocpx_wire_audit a ON a.id=p.audit_id "
				+ "WHERE a.raw_expires_at<NOW(3) LIMIT " + BATCH_SIZE + ") expired_payloads)");
		int expired = drain("UPDATE scx_ocpx_wire_audit SET raw_state='EXPIRED' "
				+ "WHERE raw_expires_at<NOW(3) AND raw_state<>'EXPIRED' LIMIT " + BATCH_SIZE);
		int metadata = drain("DELETE FROM scx_ocpx_wire_audit WHERE metadata_expires_at<NOW(3) LIMIT " + BATCH_SIZE);
		int orphans = drain("DELETE FROM scx_ocpx_wire_audit_payload WHERE audit_id IN (SELECT audit_id FROM "
				+ "(SELECT p.audit_id FROM scx_ocpx_wire_audit_payload p LEFT JOIN scx_ocpx_wire_audit a ON a.id=p.audit_id "
				+ "WHERE a.id IS NULL LIMIT " + BATCH_SIZE + ") orphan_payloads)");
		lastCleanupEpochSeconds.set(Instant.now().getEpochSecond());
		meterRegistry.counter("scx.ocpx.audit.cleanup.actions", "kind", "payload_expired").increment(payloads);
		meterRegistry.counter("scx.ocpx.audit.cleanup.actions", "kind", "metadata_expired").increment(metadata);
		meterRegistry.counter("scx.ocpx.audit.cleanup.actions", "kind", "orphan_removed").increment(orphans);
		log.info("wire audit retention completed payloads:{} expired:{} metadata:{} orphans:{}", payloads, expired,
				metadata, orphans);
	}

	private int markInterrupted() {
		return drain("UPDATE scx_ocpx_wire_audit SET result_status='INTERRUPTED',raw_state='INTERRUPTED',"
				+ "error_code='PROCESS_INTERRUPTED',finished_at=NOW(3) WHERE result_status='PENDING' "
				+ "AND captured_at<DATE_SUB(NOW(3),INTERVAL 5 MINUTE) LIMIT " + BATCH_SIZE);
	}

	private int drain(String sql) {
		int total = 0;
		int changed;
		do {
			changed = jdbcTemplate.update(sql);
			total += changed;
		}
		while (changed == BATCH_SIZE);
		return total;
	}

}
