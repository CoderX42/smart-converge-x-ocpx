package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.config.AdxTrackingProperties;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Applies an immutable version locally and writes instance evidence. It is disabled by
 * default, performs no outbound call, and treats Redis only as an acceleration signal;
 * the scheduled database scan provides crash recovery.
 */
@Component
@ConditionalOnProperty(prefix = "scx.adx.tracking", name = "distribution-worker-enabled", havingValue = "true")
@RequiredArgsConstructor
public class AdxRouteDistributionApplyWorker implements MessageListener {

	private final RouteConfigurationRepository routeRepository;

	private final RouteConfigurationCache routeCache;

	private final AdxRouteDistributionService distributionService;

	private final AdxTrackingProperties properties;

	private final String instanceId = "runtime-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

	@Scheduled(fixedDelay = 1000)
	public void applyPending() {
		if (!properties.isDistributionWorkerEnabled()) {
			return;
		}
		routeRepository.findDistributable(100).forEach(this::apply);
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		if (!properties.isDistributionWorkerEnabled()) {
			return;
		}
		RouteSignal signal = parseSignal(new String(message.getBody(), StandardCharsets.UTF_8));
		if (signal == null) {
			return;
		}
		routeRepository.findDistributable(signal.routeVersionId(), signal.routeVersion()).ifPresent(this::apply);
	}

	void apply(RouteConfigurationRepository.DistributableRoute candidate) {
		try {
			routeRepository.validateDistributableSnapshot(candidate);
			distributionService.acknowledge(candidate.distributionId(), instanceId, candidate.routeVersion(), "APPLIED",
					null);
			routeCache.reloadAll();
		}
		catch (RuntimeException error) {
			distributionService.acknowledge(candidate.distributionId(), instanceId, candidate.routeVersion(), "FAILED",
					"SNAPSHOT_APPLY_FAILED");
			try {
				routeCache.reloadAll();
			}
			catch (RuntimeException ignored) {
				// The next database scan or Redis invalidation retries the local rebuild.
			}
		}
	}

	static RouteSignal parseSignal(String value) {
		if (value == null) {
			return null;
		}
		String[] parts = value.split(":", -1);
		if (parts.length != 3 || !"ROUTE_PUBLISHED".equals(parts[2]) || !parts[0].matches("^[a-f0-9]{32}$")
				|| !parts[1].matches("^[1-9][0-9]{0,18}$")) {
			return null;
		}
		try {
			return new RouteSignal(parts[0], Long.parseLong(parts[1]));
		}
		catch (NumberFormatException error) {
			return null;
		}
	}

	record RouteSignal(String routeVersionId, long routeVersion) {
	}

}
