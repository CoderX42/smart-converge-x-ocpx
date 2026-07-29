package com.yzytech.scx.inventory.adapter;

import com.yzytech.scx.inventory.dto.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sandbox media adapter for local development and testing.
 * <p>
 * Supports controllable fault injection via {@link #injectFault(String, int)}. External
 * IDs are simulated with "sandbox-" prefix for traceability.
 */
@Component
public class GenericSandboxMediaAdapter implements MediaAdapter {

	private final Map<String, String> externalIdMap = new ConcurrentHashMap<>();

	private final Map<String, String> statusStore = new ConcurrentHashMap<>();

	private final Map<String, ReportPullResult> reportStore = new ConcurrentHashMap<>();

	private final Map<String, AtomicLong> rateLimiters = new ConcurrentHashMap<>();

	// Fault injection
	private volatile double faultRate = 0.0; // 0.0-1.0

	private volatile int timeoutMs = 0; // simulated timeout

	private volatile boolean healthy = true;

	@Override
	public String adapterType() {
		return "GENERIC_SANDBOX";
	}

	@Override
	public CapabilityView getCapabilities() {
		return new CapabilityView("GENERIC_SANDBOX", List.of("CPM", "CPC", "MEDIA_MANAGED_OCPC"),
				List.of("CLICK", "LANDING", "ACTIVATE", "REGISTER", "PAY"), List.of("IMAGE", "VIDEO", "HTML", "TEXT"),
				true, true, true);
	}

	@Override
	public List<com.yzytech.scx.inventory.entity.Placement> syncPlacements(String mediaAccountId, String cursor) {
		// Sandbox: return empty — placements are manually configured
		return List.of();
	}

	@Override
	public PublishResult publishCampaign(PublishRequest req) {
		checkFault("publish");
		checkRateLimit(req.placementId());
		simulateTimeout();

		String extId = externalIdMap.computeIfAbsent(req.campaignId(),
				k -> "sandbox-ext-" + UUID.randomUUID().toString().substring(0, 8));
		statusStore.put(extId, "DELIVERING");

		return new PublishResult(true, extId, "DELIVERING", null, null, LocalDateTime.now());
	}

	@Override
	public PublishResult changeStatus(String externalCampaignId, String newStatus, String idempotencyKey) {
		checkFault("changeStatus");
		statusStore.put(externalCampaignId, newStatus);
		return new PublishResult(true, externalCampaignId, newStatus, null, null, LocalDateTime.now());
	}

	@Override
	public StatusQueryResult queryStatus(String externalCampaignId) {
		checkFault("queryStatus");
		String status = statusStore.getOrDefault(externalCampaignId, "UNKNOWN");
		return new StatusQueryResult(externalCampaignId, status, LocalDateTime.now(), true);
	}

	@Override
	public ReportPullResult pullReport(String mediaAccountId, String startDate, String endDate, String cursor) {
		checkFault("pullReport");
		String key = mediaAccountId + "|" + startDate + "|" + endDate;
		return reportStore.getOrDefault(key, new ReportPullResult(null, false, List.of(), LocalDateTime.now()));
	}

	@Override
	public ReportPullResult pullBilling(String mediaAccountId, String startDate, String endDate, String cursor) {
		checkFault("pullBilling");
		// Sandbox billing mirrors report spend
		return pullReport(mediaAccountId, startDate, endDate, cursor);
	}

	@Override
	public boolean isHealthy() {
		return healthy;
	}

	// ── Fault injection (sandbox only) ──

	public void injectFault(String fault, int value) {
		switch (fault) {
			case "rate" -> this.faultRate = Math.min(1.0, Math.max(0.0, value / 100.0));
			case "timeout" -> this.timeoutMs = value;
			case "down" -> this.healthy = value == 0;
			case "reset" -> {
				faultRate = 0.0;
				timeoutMs = 0;
				healthy = true;
			}
		}
	}

	private void checkFault(String operation) {
		if (!healthy)
			throw new RuntimeException("Sandbox adapter is DOWN");
		if (Math.random() < faultRate)
			throw new RuntimeException("Sandbox simulated fault for: " + operation);
	}

	private void checkRateLimit(String key) {
		AtomicLong counter = rateLimiters.computeIfAbsent(key, k -> new AtomicLong());
		if (counter.incrementAndGet() > 100) // hard cap for sandbox
			throw new RuntimeException("Sandbox rate limit exceeded for: " + key);
	}

	private void simulateTimeout() {
		if (timeoutMs > 0) {
			try {
				Thread.sleep(timeoutMs);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
