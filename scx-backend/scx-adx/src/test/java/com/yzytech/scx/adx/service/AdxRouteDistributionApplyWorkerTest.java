package com.yzytech.scx.adx.service;

import com.yzytech.scx.adx.config.AdxTrackingProperties;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdxRouteDistributionApplyWorkerTest {

	@Test
	void defaultProfileDoesNotPollDatabaseOrTouchRuntime() {
		RouteConfigurationRepository repository = mock(RouteConfigurationRepository.class);
		RouteConfigurationCache cache = mock(RouteConfigurationCache.class);
		AdxRouteDistributionService distribution = mock(AdxRouteDistributionService.class);
		AdxTrackingProperties properties = new AdxTrackingProperties();
		properties.setDistributionWorkerEnabled(false);

		new AdxRouteDistributionApplyWorker(repository, cache, distribution, properties).applyPending();

		verifyNoInteractions(repository, cache, distribution);
	}

	@Test
	void acceptsOnlyExactPublishedRouteSignals() {
		String id = "0123456789abcdef0123456789abcdef";
		assertThat(AdxRouteDistributionApplyWorker.parseSignal(id + ":7:ROUTE_PUBLISHED"))
			.isEqualTo(new AdxRouteDistributionApplyWorker.RouteSignal(id, 7));
		assertThat(AdxRouteDistributionApplyWorker.parseSignal(id + ":7:ROUTE_DENIED")).isNull();
		assertThat(AdxRouteDistributionApplyWorker.parseSignal("route:7:ROUTE_PUBLISHED")).isNull();
	}

	@Test
	void validatesAnImmutableSnapshotBeforeWritingAppliedEvidence() {
		Fixture fixture = fixture();
		RouteConfigurationRepository.DistributableRoute candidate = candidate();
		when(fixture.repository().validateDistributableSnapshot(candidate)).thenReturn(mock(RouteSnapshot.class));

		fixture.worker().apply(candidate);

		verify(fixture.distribution()).acknowledge(eq(candidate.distributionId()), anyString(),
				eq(candidate.routeVersion()), eq("APPLIED"), isNull());
		verify(fixture.cache()).reloadAll();
	}

	@Test
	void recordsAControlledFailureWhenTheFrozenSnapshotCannotBeApplied() {
		Fixture fixture = fixture();
		RouteConfigurationRepository.DistributableRoute candidate = candidate();
		when(fixture.repository().validateDistributableSnapshot(candidate))
			.thenThrow(new IllegalStateException("invalid frozen snapshot"));

		fixture.worker().apply(candidate);

		verify(fixture.distribution()).acknowledge(eq(candidate.distributionId()), anyString(),
				eq(candidate.routeVersion()), eq("FAILED"), eq("SNAPSHOT_APPLY_FAILED"));
		verify(fixture.cache()).reloadAll();
	}

	private Fixture fixture() {
		RouteConfigurationRepository repository = mock(RouteConfigurationRepository.class);
		RouteConfigurationCache cache = mock(RouteConfigurationCache.class);
		AdxRouteDistributionService distribution = mock(AdxRouteDistributionService.class);
		AdxTrackingProperties properties = new AdxTrackingProperties();
		properties.setDistributionWorkerEnabled(true);
		return new Fixture(repository, cache, distribution,
				new AdxRouteDistributionApplyWorker(repository, cache, distribution, properties));
	}

	private RouteConfigurationRepository.DistributableRoute candidate() {
		return new RouteConfigurationRepository.DistributableRoute("distribution-1", "0123456789abcdef0123456789abcdef",
				7);
	}

	private record Fixture(RouteConfigurationRepository repository, RouteConfigurationCache cache,
			AdxRouteDistributionService distribution, AdxRouteDistributionApplyWorker worker) {
	}

}
