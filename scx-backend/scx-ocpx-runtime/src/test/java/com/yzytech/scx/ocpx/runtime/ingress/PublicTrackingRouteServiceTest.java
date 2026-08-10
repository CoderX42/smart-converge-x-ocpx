package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicTrackingRouteServiceTest {

	private final RouteConfigurationCache cache = mock(RouteConfigurationCache.class);

	private final PublicTrackingRouteService service = new PublicTrackingRouteService(cache, new ObjectMapper());

	@Test
	void resolvesFrozenDestinationWithoutPerformingNetworkIo() {
		when(cache.requirePublicRoute("route-code")).thenReturn(new RouteConfigurationCache.PublicRoute(route(),
				"https://destination.test/click?clickid=__CLICKID__&callback=__CALLBACK__", schema()));
		LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
		query.add("clickid", "fact id");
		query.add("callback", "https://callback.test/a?x=1");

		PublicTrackingRouteService.Resolution result = service.resolve("route-code", query);

		assertThat(result.destination().toString())
			.isEqualTo("https://destination.test/click?clickid=fact%20id&callback=https://callback.test/a?x%3D1");
		assertThat(result.routeVersion()).isEqualTo(3);
	}

	@Test
	void rejectsMissingDuplicateUnknownAndUnresolvedMacros() {
		when(cache.requirePublicRoute("route-code")).thenReturn(new RouteConfigurationCache.PublicRoute(route(),
				"https://destination.test/click?clickid=__CLICKID__", schema()));
		assertThatThrownBy(() -> service.resolve("route-code", new LinkedMultiValueMap<>()))
			.isInstanceOf(OcpxException.class);
		LinkedMultiValueMap<String, String> duplicate = new LinkedMultiValueMap<>();
		duplicate.add("clickid", "one");
		duplicate.add("clickid", "two");
		assertThatThrownBy(() -> service.resolve("route-code", duplicate)).isInstanceOf(OcpxException.class);
		LinkedMultiValueMap<String, String> unknown = new LinkedMultiValueMap<>();
		unknown.add("clickid", "one");
		unknown.add("extra", "two");
		assertThatThrownBy(() -> service.resolve("route-code", unknown)).isInstanceOf(OcpxException.class);
	}

	private String schema() {
		return """
				{"pairs":[
				 {"key":"clickid","macro":"__CLICKID__","required":true},
				 {"key":"callback","macro":"__CALLBACK__","required":false}
				]}
				""";
	}

	private RouteSnapshot route() {
		RouteEventSnapshot event = new RouteEventSnapshot("mapping", "PAY", "PAY", "PAY", "PAY", "PAY", "PAY",
				BigDecimal.ZERO, 0);
		return new RouteSnapshot("monitor", "biz", 3, "channel", "CHANNEL", "ADAPTER", "NONE", 1, "key", "advertiser",
				"link", "UPSTREAM", "secret", "https://bid.test", "", null, BigDecimal.ZERO, BigDecimal.ONE, "OFF",
				"OFF", 3600, List.of(event), 2, "SNAPSHOT");
	}

}
