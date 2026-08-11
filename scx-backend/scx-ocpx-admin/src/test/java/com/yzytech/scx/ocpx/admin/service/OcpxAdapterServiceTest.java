package com.yzytech.scx.ocpx.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OcpxAdapterServiceTest {

	@Test
	void parsesProtocolDrivenMacroRules() {
		OcpxAdapterService service = new OcpxAdapterService(mock(JdbcTemplate.class), new ObjectMapper());

		OcpxAdapterService.PublishedAdapter adapter = service.parse("TEMPLATE", "UPSTREAM", """
				{"feedbackMethod":"GET","callbackMethod":"GET",
				 "supportedMacros":["__EVENT__","__CALLBACK__","__CALLBACK_URL__"],
				 "requiredUrlMacros":["__EVENT__"],
				 "requiredAnyUrlMacros":[["__CALLBACK__","__CALLBACK_URL__"]]}
				""");

		assertThat(adapter.supportedMacros()).contains("__EVENT__", "__CALLBACK_URL__");
		assertThat(adapter.requiredUrlMacros()).containsExactly("__EVENT__");
		assertThat(adapter.requiredAnyUrlMacros()).singleElement()
			.satisfies(group -> assertThat(group).containsExactlyInAnyOrder("__CALLBACK__", "__CALLBACK_URL__"));
	}

}
