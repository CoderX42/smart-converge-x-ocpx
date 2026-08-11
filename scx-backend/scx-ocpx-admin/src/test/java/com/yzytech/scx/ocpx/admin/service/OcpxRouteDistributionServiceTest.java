package com.yzytech.scx.ocpx.admin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcpxRouteDistributionServiceTest {

	@Test
	void neverReportsActiveBeforeAckThresholdAndSurfacesPartialFailure() {
		assertThat(OcpxRouteDistributionService.resolveDistributionStatus(2, 0, 0)).isEqualTo("DISTRIBUTING");
		assertThat(OcpxRouteDistributionService.resolveDistributionStatus(2, 1, 0)).isEqualTo("DISTRIBUTING");
		assertThat(OcpxRouteDistributionService.resolveDistributionStatus(2, 2, 0)).isEqualTo("ACTIVE");
		assertThat(OcpxRouteDistributionService.resolveDistributionStatus(2, 2, 1)).isEqualTo("DEGRADED");
		assertThat(OcpxRouteDistributionService.resolveDistributionStatus(2, 0, 1)).isEqualTo("DEGRADED");
	}

	@Test
	void rejectsCorruptCounters() {
		assertThatThrownBy(() -> OcpxRouteDistributionService.resolveDistributionStatus(0, 0, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OcpxRouteDistributionService.resolveDistributionStatus(1, -1, 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
