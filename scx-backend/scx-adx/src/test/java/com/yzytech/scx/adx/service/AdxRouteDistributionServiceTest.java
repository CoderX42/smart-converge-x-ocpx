package com.yzytech.scx.adx.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdxRouteDistributionServiceTest {

	@Test
	void neverReportsActiveBeforeAckThresholdAndSurfacesPartialFailure() {
		assertThat(AdxRouteDistributionService.resolveDistributionStatus(2, 0, 0)).isEqualTo("DISTRIBUTING");
		assertThat(AdxRouteDistributionService.resolveDistributionStatus(2, 1, 0)).isEqualTo("DISTRIBUTING");
		assertThat(AdxRouteDistributionService.resolveDistributionStatus(2, 2, 0)).isEqualTo("ACTIVE");
		assertThat(AdxRouteDistributionService.resolveDistributionStatus(2, 2, 1)).isEqualTo("DEGRADED");
		assertThat(AdxRouteDistributionService.resolveDistributionStatus(2, 0, 1)).isEqualTo("DEGRADED");
	}

	@Test
	void rejectsCorruptCounters() {
		assertThatThrownBy(() -> AdxRouteDistributionService.resolveDistributionStatus(0, 0, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AdxRouteDistributionService.resolveDistributionStatus(1, -1, 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
