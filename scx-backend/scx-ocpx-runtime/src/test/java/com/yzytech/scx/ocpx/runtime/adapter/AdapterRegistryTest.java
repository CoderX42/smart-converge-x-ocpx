package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdapterRegistryTest {

	@Test
	void normalizesCodesAndRejectsDuplicateRegistration() {
		UpstreamAdapter adapter = upstream(" demo ");
		AdapterRegistry registry = new AdapterRegistry(List.of(adapter), List.of());

		assertThat(registry.requireUpstream("DeMo")).isSameAs(adapter);
		assertThatThrownBy(() -> new AdapterRegistry(List.of(adapter, upstream("DEMO")), List.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("duplicate");
	}

	private UpstreamAdapter upstream(String code) {
		UpstreamAdapter adapter = mock(UpstreamAdapter.class);
		when(adapter.code()).thenReturn(code);
		when(adapter.capabilities()).thenReturn(new AdapterCapabilities("UPSTREAM", Set.of(), Set.of("GET"),
				Set.of("NONE"), Duration.ofSeconds(3), Set.of(), true));
		return adapter;
	}

}
