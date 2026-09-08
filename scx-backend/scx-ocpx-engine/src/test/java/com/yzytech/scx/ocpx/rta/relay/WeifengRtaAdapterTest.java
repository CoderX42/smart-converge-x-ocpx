package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;

class WeifengRtaAdapterTest {

	final WeifengRtaAdapter adapter = new WeifengRtaAdapter();

	Upstream upstream() {
		return new Upstream("u", "a", "code", "https://rta.adapi.cn/rtaCommon/sendRta", "", 1, 5000, 300, true,
				"WEIFENG_RTA", 1, Map.of("aid", "5103", "did", "zsyd"));
	}

	RtaState state() {
		var state = new RtaState();
		state.setRelayRequestId("a".repeat(32));
		state.setProviderRequestId(state.getRelayRequestId());
		state.setUpstream(upstream());
		state.setBindings(List.of(new Binding("task", "a", "m", "l", "u", null, null, 1)));
		return state;
	}

	@Test
	void wireMappingHashesOnceAndPreservesCaidVersions() {
		var request = new Request("media-id", List.of("task"),
				Map.of("oaid", "test", "idfaMd5", "ABCDEF0123456789ABCDEF0123456789", "caid", "new-caid", "caidVersion",
						"20260506", "preCaid", "old-caid", "preCaidVersion", "20250325"),
				Map.of("os", "ANDROID", "ip", "203.0.113.1"));
		var outbound = adapter.build(state(), request, "");
		assertThat(outbound.endpoint()).isEqualTo("https://rta.adapi.cn/rtaCommon/sendRta?aid=5103&did=zsyd");
		assertThat(outbound.credentialRef()).isEmpty();
		var json = RelayJson.read(outbound.body(), com.fasterxml.jackson.databind.JsonNode.class);
		assertThat(json.path("device").path("oaid_md5sum").asText()).isEqualTo("098f6bcd4621d373cade4e832627b4f6");
		assertThat(json.path("device").path("idfa_md5sum").asText()).isEqualTo("abcdef0123456789abcdef0123456789");
		assertThat(json.path("device").path("caid_new").asText()).isEqualTo("new-caid");
		assertThat(json.path("device").path("caid_version").asText()).isEqualTo("20250325");
		assertThat(outbound.body()).doesNotContain("callbackUrl", "candidates", "media-id", "\"oaid\":");
	}

	@Test
	void exactResponseIdentityAndIntegerDecisionAreRequired() {
		var state = state();
		for (int code : List.of(0, 1, -1)) {
			var result = adapter.submitted(state, new RtaUpstreamAdapter.HttpResult(200,
					RelayJson.write(Map.of("id", state.getProviderRequestId(), "code", code))));
			assertThat(result.status()).isEqualTo(code == 0 ? "ALLOW" : "DENY");
			assertThat(result.matched()).hasSize(code == 0 ? 1 : 0);
		}
		for (String json : List.of("{}", "{\"id\":\"wrong\",\"code\":0}",
				"{\"id\":\"" + state.getProviderRequestId() + "\",\"code\":\"0\"}"))
			assertThatThrownBy(() -> adapter.submitted(state, new RtaUpstreamAdapter.HttpResult(200, json)))
				.isInstanceOf(RelayException.class);
		assertThatThrownBy(() -> adapter.submitted(state, new RtaUpstreamAdapter.HttpResult(500, "{}")))
			.hasMessage("WEIFENG_HTTP_ERROR");
		assertThatThrownBy(() -> adapter.callback(upstream(), "/callback", "{}", Map.of(), null))
			.hasMessage("RTA_CALLBACK_UNSUPPORTED");
	}

	@Test
	void rejectsMultiTaskBadConfigAndQueryInjection() {
		assertThatThrownBy(() -> adapter
			.validate(new Request("r", List.of("a", "b"), Map.of("oaid", "test"), Map.of("os", "android")), upstream()))
			.hasMessage("RTA_SINGLE_TASK_REQUIRED");
		assertThatThrownBy(() -> adapter.definition().validateConfig(Map.of("aid", "5103", "did", "zsyd&aid=other")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(RelayHttpTransport.validQuery(URI.create("https://rta.adapi.cn/rtaCommon/sendRta?aid=5103&did=zsyd"),
				Set.of("aid", "did")))
			.isTrue();
		for (String query : List.of("aid=1&aid=2&did=x", "aid=1&did=x&secret=y", "aid=1", "aid=1&did="))
			assertThat(RelayHttpTransport.validQuery(URI.create("https://a.test/?" + query), Set.of("aid", "did")))
				.isFalse();
		assertThat(RelayHttpTransport.validQuery(URI.create("https://a.test/?aid=1"), Set.of())).isFalse();
		assertThatThrownBy(() -> RtaProtocols.requireWeifengRoute("https://ad.adapi.cn/click?aid=1&did=zsyd",
				upstream().protocolConfig()))
			.hasMessageContaining("不一致");
		assertThatThrownBy(() -> new RtaAdapterRegistry(List.of(adapter, adapter)))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new RtaAdapterRegistry(List.of(adapter)).require("unknown"))
			.hasMessage("RTA_PROTOCOL_UNAVAILABLE");
	}

}
