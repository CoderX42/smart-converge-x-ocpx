package com.yzytech.scx.ocpx.runtime.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import com.yzytech.scx.ocpx.runtime.ingress.IngressAuditRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

class UpstreamCallbackControllerTest {

	private final UpstreamCallbackDispatcher dispatcher = org.mockito.Mockito.mock(UpstreamCallbackDispatcher.class);

	private final IngressAuditRecorder auditRecorder = mock(IngressAuditRecorder.class);

	private final UpstreamCallbackController controller = new UpstreamCallbackController(dispatcher, auditRecorder);

	private final HttpHeaders headers = new HttpHeaders();

	@Test
	void writesAdapterDefinedSuccessResponseWithoutInterpretingItsBody() {
		Map<String, String> parameters = Collections.singletonMap("vendor_token", "token-1");
		Map<String, String> responseHeaders = Collections.singletonMap("X-Vendor", "accepted");
		UpstreamCallbackResponse callbackResponse = new UpstreamCallbackResponse(202, responseHeaders, "VENDOR_OK");
		when(dispatcher.dispatch("vendor", parameters))
			.thenReturn(Mono.just(UpstreamCallbackDispatcher.DispatchResult.handled(callbackResponse)));

		ResponseEntity<Object> response = controller.callback("vendor", parameters, headers, exchange()).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(202);
		assertThat(response.getHeaders().getFirst("X-Vendor")).isEqualTo("accepted");
		assertThat(response.getBody()).isEqualTo("VENDOR_OK");
		verifyNoInteractions(auditRecorder);
	}

	@Test
	void writesAdapterDefinedJsonBodyWithoutUsingACommonResultWrapper() {
		Map<String, String> parameters = Collections.singletonMap("vendor_token", "token-1");
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("vendor_code", 9001);
		UpstreamCallbackResponse callbackResponse = new UpstreamCallbackResponse(200, Collections.emptyMap(), body);
		when(dispatcher.dispatch("vendor", parameters))
			.thenReturn(Mono.just(UpstreamCallbackDispatcher.DispatchResult.handled(callbackResponse)));

		ResponseEntity<Object> response = controller.callback("vendor", parameters, headers, exchange()).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).isEqualTo(body);
	}

	@Test
	void returnsEmptyNotFoundWhenNoUpstreamProtocolIsRegistered() {
		when(dispatcher.dispatch("unknown", Collections.emptyMap()))
			.thenReturn(Mono.just(UpstreamCallbackDispatcher.DispatchResult.upstreamNotFound()));

		ResponseEntity<Object> response = controller.callback("unknown", Collections.emptyMap(), headers, exchange())
			.block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).isNull();
		verify(auditRecorder).recordUpstreamCallback(eq("unknown"), eq("UPSTREAM_NOT_FOUND"), eq(404),
				eq("upstream adapter is not registered"), eq(Collections.emptyMap()), eq(headers), eq(null), anyLong());
	}

	@Test
	void auditsProtocolRejectionEvenWhenAdapterControlsTheResponse() {
		Map<String, String> parameters = Collections.singletonMap("ext", "private-token");
		UpstreamCallbackResponse callbackResponse = new UpstreamCallbackResponse(200, Collections.emptyMap(),
				Collections.singletonMap("code", 1));
		when(dispatcher.dispatch("coco", parameters)).thenReturn(
				Mono.just(UpstreamCallbackDispatcher.DispatchResult.rejected(callbackResponse, 400, "invalid ext")));

		ResponseEntity<Object> response = controller.callback("coco", parameters, headers, exchange()).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		verify(auditRecorder).recordUpstreamCallback(eq("coco"), eq("PROTOCOL_REJECTED"), eq(200), eq("invalid ext"),
				eq(parameters), eq(headers), eq(null), anyLong());
	}

	@Test
	void auditsUnexpectedPersistenceFailureAsInternalError() {
		Map<String, String> parameters = Collections.singletonMap("ext", "private-token");
		when(dispatcher.dispatch("coco", parameters))
			.thenReturn(Mono.error(new IllegalStateException("insert failed")));

		assertThatThrownBy(() -> controller.callback("coco", parameters, headers, exchange()).block())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("insert failed");
		verify(auditRecorder).recordUpstreamCallback(eq("coco"), eq("INTERNAL_ERROR"), eq(500), eq("insert failed"),
				eq(parameters), eq(headers), eq(null), anyLong());
	}

	private ServerWebExchange exchange() {
		ServerWebExchange exchange = mock(ServerWebExchange.class);
		ServerHttpRequest request = mock(ServerHttpRequest.class);
		when(exchange.getRequest()).thenReturn(request);
		return exchange;
	}

}
