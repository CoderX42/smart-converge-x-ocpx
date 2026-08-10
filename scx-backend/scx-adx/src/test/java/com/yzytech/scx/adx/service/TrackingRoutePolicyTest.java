package com.yzytech.scx.adx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.adx.config.AdxTrackingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingRoutePolicyTest {

	private final AdxTrackingProperties properties = new AdxTrackingProperties();

	private TrackingRoutePolicy policy;

	@BeforeEach
	void setUp() {
		properties.setPublicBaseUrl("https://tracking.test");
		policy = new TrackingRoutePolicy(properties, new ObjectMapper());
	}

	@Test
	void producesCryptographicallySizedNonSequentialRouteCodesAndServerTemplate() {
		Set<String> codes = new HashSet<>();
		for (int index = 0; index < 64; index++) {
			String code = policy.newRouteCode();
			assertThat(code).matches("^[A-Za-z0-9_-]{43}$");
			codes.add(code);
		}
		assertThat(codes).hasSize(64);
		String template = policy.buildTemplate(policy.newRouteCode(), adapter());
		assertThat(template).startsWith("https://tracking.test/ocpx/c/")
			.contains("clickid=__CLICKID__", "callback=__CALLBACK__");
	}

	@Test
	void failsClosedForUnconfiguredHostAndInvalidMacroCapability() {
		properties.setPublicBaseUrl("https://tracking.invalid");
		assertThat(policy.assess(adapter()).publishable()).isFalse();
		AdxAdapterService.PublishedAdapter duplicate = adapter(
				List.of(new AdxAdapterService.TrackingMacroPair("clickid", "__CLICKID__", true),
						new AdxAdapterService.TrackingMacroPair("clickid", "__OTHER__", false)));
		assertThat(policy.assess(duplicate).publishable()).isFalse();
	}

	@Test
	void rejectsHttpUnknownDuplicateMissingEncodedAndUnsafeCallbackMacros() {
		List<AdxAdapterService.TrackingMacroPair> allowed = adapter().trackingMacroPairs();
		assertThatThrownBy(() -> policy.validateTemplate("http://tracking.test/ocpx/c/x?clickid=__CLICKID__", allowed))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy
			.validateTemplate("https://tracking.test/ocpx/c/x?clickid=__CLICKID__&unknown=__UNKNOWN__", allowed))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy
			.validateTemplate("https://tracking.test/ocpx/c/x?clickid=__CLICKID__&clickid=__CLICKID__", allowed))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> policy.validateTemplate("https://tracking.test/ocpx/c/x?callback=__CALLBACK__", allowed))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> policy.validateTemplate("https://tracking.test/ocpx/c/x?clickid=%5F%5FCLICKID%5F%5F", allowed))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy
			.validateTemplate("https://tracking.test/ocpx/c/x?clickid=__CLICKID__&callback=__CLICKID__", allowed))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private AdxAdapterService.PublishedAdapter adapter() {
		return adapter(List.of(new AdxAdapterService.TrackingMacroPair("clickid", "__CLICKID__", true),
				new AdxAdapterService.TrackingMacroPair("callback", "__CALLBACK__", false)));
	}

	private AdxAdapterService.PublishedAdapter adapter(List<AdxAdapterService.TrackingMacroPair> pairs) {
		return new AdxAdapterService.PublishedAdapter("MEDIA", "DOWNSTREAM", "POST", "POST", Set.of(), Set.of(),
				List.of(), 1, pairs);
	}

}
