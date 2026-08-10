package com.yzytech.scx.adx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.adx.dto.DashboardOverviewViews;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

class AdxDashboardCacheTest {

	@Test
	void expiresAtTheConfiguredBoundaryInsteadOfServingStaleData() {
		Clock clock = mock(Clock.class);
		Instant start = Instant.parse("2026-08-10T00:00:00Z");
		when(clock.instant()).thenReturn(start, start.plusSeconds(44), start.plusSeconds(45));
		AdxDashboardCache cache = new AdxDashboardCache(clock);
		DashboardOverviewViews.Overview overview = mock(DashboardOverviewViews.Overview.class);

		cache.put("user-scope-query-watermark", overview, 45);

		assertThat(cache.get("user-scope-query-watermark")).contains(overview);
		assertThat(cache.get("user-scope-query-watermark")).isEmpty();
	}

}
