package com.yzytech.scx.ocpx.runtime.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

class UpstreamCallbackControllerTest {

	private final UpstreamCallbackDispatcher dispatcher = org.mockito.Mockito.mock(UpstreamCallbackDispatcher.class);

	private final UpstreamCallbackController controller = new UpstreamCallbackController(dispatcher);

	@Test
	void writesAdapterDefinedSuccessResponseWithoutInterpretingItsBody() {
		Map<String, String> parameters = Collections.singletonMap("vendor_token", "token-1");
		Map<String, String> headers = Collections.singletonMap("X-Vendor", "accepted");
		UpstreamCallbackResponse callbackResponse = new UpstreamCallbackResponse(202, headers, "VENDOR_OK");
		when(dispatcher.dispatch("vendor", parameters))
			.thenReturn(UpstreamCallbackDispatcher.DispatchResult.handled(callbackResponse));

		ResponseEntity<Object> response = controller.callback("vendor", parameters);

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(202);
		assertThat(response.getHeaders().getFirst("X-Vendor")).isEqualTo("accepted");
		assertThat(response.getBody()).isEqualTo("VENDOR_OK");
	}

	@Test
	void writesAdapterDefinedJsonBodyWithoutUsingACommonResultWrapper() {
		Map<String, String> parameters = Collections.singletonMap("vendor_token", "token-1");
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("vendor_code", 9001);
		UpstreamCallbackResponse callbackResponse = new UpstreamCallbackResponse(200, Collections.emptyMap(), body);
		when(dispatcher.dispatch("vendor", parameters))
			.thenReturn(UpstreamCallbackDispatcher.DispatchResult.handled(callbackResponse));

		ResponseEntity<Object> response = controller.callback("vendor", parameters);

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).isEqualTo(body);
	}

	@Test
	void returnsEmptyNotFoundWhenNoUpstreamProtocolIsRegistered() {
		when(dispatcher.dispatch("unknown", Collections.emptyMap()))
			.thenReturn(UpstreamCallbackDispatcher.DispatchResult.upstreamNotFound());

		ResponseEntity<Object> response = controller.callback("unknown", Collections.emptyMap());

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).isNull();
	}

}
