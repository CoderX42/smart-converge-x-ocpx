package com.yzytech.scx.ocpx.runtime.config;

import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteConfigurationCacheTest {

	@Test
	void resolvesOnlyTheOpaqueRouteCodeHashLoadedFromActiveDistributionTruth() {
		RouteConfigurationRepository repository = mock(RouteConfigurationRepository.class);
		ProtocolCrypto crypto = mock(ProtocolCrypto.class);
		RouteSnapshot route = route();
		when(repository.findAllPublished())
			.thenReturn(List.of(new RouteConfigurationRepository.PublishedRoute(route, "biz", "hash",
					"https://destination.test/click?clickid=__CLICKID__",
					"{\"pairs\":[{\"key\":\"clickid\",\"macro\":\"__CLICKID__\",\"required\":true}]}")));
		when(crypto.blindIndex("OCPX_ROUTE_CODE", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO12")).thenReturn("hash");
		RouteConfigurationCache cache = new RouteConfigurationCache(repository, crypto);
		cache.reloadAll();

		assertThat(cache.requireByRouteCode("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO12")).isSameAs(route);
		assertThatThrownBy(() -> cache.requireByRouteCode("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO13"))
			.isInstanceOf(OcpxException.class);
		assertThatThrownBy(() -> cache.requireByRouteCode("monitor-id"))
			.isInstanceOf(OcpxException.class);
	}

	private RouteSnapshot route() {
		RouteEventSnapshot event = new RouteEventSnapshot("mapping", "PAY", "PAY", "PAY", "PAY", "PAY", "PAY",
				BigDecimal.ZERO, 0);
		return new RouteSnapshot("monitor", "biz", 3, "channel", "CHANNEL", "ADAPTER", "NONE", 1, "channel-key",
				"advertiser", "link", "UPSTREAM", "secret", "https://bid.test", "", null, BigDecimal.ZERO,
				BigDecimal.ONE, "OFF", "OFF", 3600, List.of(event), 2, "SNAPSHOT");
	}

}
