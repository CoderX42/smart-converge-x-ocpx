package com.yzytech.scx.ocpx.rta.adapter;

import com.yzytech.scx.ocpx.rta.enums.RtaDecision;
import com.yzytech.scx.ocpx.rta.model.RtaAdapterResult;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionRequest;
import com.yzytech.scx.ocpx.rta.model.RtaProfileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;



class RtaMockDecisionAdapterTest {

	private final RtaMockDecisionAdapter adapter = new RtaMockDecisionAdapter();

	private final RtaDecisionRequest request = new RtaDecisionRequest();

	@Test
	void allowUsesConfiguredTaskIds() {
		RtaAdapterResult result = adapter.decide(request,
				new RtaProfileSnapshot("profile-1", "MOCK", "{\"decision\":\"ALLOW\",\"taskIds\":[\"ext-1\"]}"),
				List.of("ext-1", "binding-1"));

		assertThat(result.decision()).isEqualTo(RtaDecision.ALLOW);
		assertThat(result.reasonCode()).isEqualTo("MOCK_ALLOW");
		assertThat(result.taskIds()).containsExactly("ext-1");
	}

	@Test
	void denyAndErrorNeverProduceAllow() {
		RtaAdapterResult deny = adapter.decide(request,
				new RtaProfileSnapshot("profile-1", "MOCK", "{\"decision\":\"DENY\"}"), List.of("ext-1"));
		RtaAdapterResult error = adapter.decide(request,
				new RtaProfileSnapshot("profile-1", "MOCK", "{\"decision\":\"ERROR\"}"), List.of("ext-1"));

		assertThat(deny.decision()).isEqualTo(RtaDecision.DENY);
		assertThat(deny.reasonCode()).isEqualTo("MOCK_DENY");
		assertThat(error.decision()).isEqualTo(RtaDecision.DENY);
		assertThat(error.outcomeClass()).isEqualTo("ERROR");
		assertThat(error.reasonCode()).isEqualTo("MOCK_ERROR");
	}

	@Test
	void configuredTaskIdsWithoutBoundTaskBecomeNoMatch() {
		RtaAdapterResult result = adapter.decide(request,
				new RtaProfileSnapshot("profile-1", "MOCK", "{\"decision\":\"ALLOW\",\"taskIds\":[\"other\"]}"),
				List.of("ext-1"));

		assertThat(result.decision()).isEqualTo(RtaDecision.DENY);
		assertThat(result.outcomeClass()).isEqualTo("REJECTED");
		assertThat(result.reasonCode()).isEqualTo("RTA_NO_MATCH");
	}

}
