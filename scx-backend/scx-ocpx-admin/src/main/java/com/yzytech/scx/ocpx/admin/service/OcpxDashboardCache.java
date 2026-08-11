package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.DashboardOverviewViews;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OcpxDashboardCache {

	private static final int MAXIMUM_ENTRIES = 512;

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	private final Clock clock;

	public OcpxDashboardCache() {
		this(Clock.systemUTC());
	}

	OcpxDashboardCache(Clock clock) {
		this.clock = clock;
	}

	public Optional<DashboardOverviewViews.Overview> get(String key) {
		Entry entry = entries.get(key);
		if (entry == null) {
			return Optional.empty();
		}
		if (!clock.instant().isBefore(entry.expiresAt())) {
			entries.remove(key, entry);
			return Optional.empty();
		}
		return Optional.of(entry.value());
	}

	public void put(String key, DashboardOverviewViews.Overview value, int ttlSeconds) {
		if (entries.size() >= MAXIMUM_ENTRIES) {
			entries.entrySet()
				.stream()
				.min(Comparator.comparing(item -> item.getValue().expiresAt()))
				.ifPresent(item -> entries.remove(item.getKey(), item.getValue()));
		}
		entries.put(key, new Entry(value, clock.instant().plusSeconds(ttlSeconds)));
	}

	void clear() {
		entries.clear();
	}

	private record Entry(DashboardOverviewViews.Overview value, Instant expiresAt) {
	}

}
