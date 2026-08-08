package com.yzytech.scx.ocpx.runtime.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterNotFoundException;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

class UpstreamCallbackDispatcherTest {

	private final CallbackIngressService callbackIngressService = mock(CallbackIngressService.class);

	private final AdapterRegistry adapterRegistry = mock(AdapterRegistry.class);

	private final UpstreamAdapter adapter = mock(UpstreamAdapter.class);

	private final UpstreamCallbackDispatcher dispatcher = new UpstreamCallbackDispatcher(callbackIngressService,
			adapterRegistry);

	@Test
	void delegatesSuccessResponseToTheSelectedAdapter() {
		Map<String, String> parameters = Collections.singletonMap("vendor_token", "token-1");
		UpstreamCallbackResponse expected = new UpstreamCallbackResponse(200, Collections.emptyMap(), "OK");
		when(adapterRegistry.requireUpstream("VENDOR")).thenReturn(adapter);
		when(callbackIngressService.accept(adapter, parameters))
			.thenReturn(Mono.just(new CallbackIngressService.Result("event-1", "ACCEPTED")));
		when(adapter.callbackSuccess(parameters, "event-1", "ACCEPTED")).thenReturn(Mono.just(expected));

		UpstreamCallbackDispatcher.DispatchResult result = dispatcher.dispatch(" vendor ", parameters).block();

		assertThat(result.isUpstreamFound()).isTrue();
		assertThat(result.getResponse()).isSameAs(expected);
	}

	@Test
	void delegatesProtocolFailureResponseToTheSelectedAdapter() {
		Map<String, String> parameters = Collections.singletonMap("vendor_token", "token-1");
		UpstreamCallbackResponse expected = new UpstreamCallbackResponse(200, Collections.emptyMap(), "VENDOR_ERROR");
		when(adapterRegistry.requireUpstream("VENDOR")).thenReturn(adapter);
		when(callbackIngressService.accept(adapter, parameters))
			.thenReturn(Mono.error(new OcpxException(HttpStatus.BAD_REQUEST, "bad callback")));
		when(adapter.callbackFailure(parameters, 400, "bad callback")).thenReturn(Mono.just(expected));

		UpstreamCallbackDispatcher.DispatchResult result = dispatcher.dispatch("vendor", parameters).block();

		assertThat(result.isUpstreamFound()).isTrue();
		assertThat(result.getResponse()).isSameAs(expected);
	}

	@Test
	void returnsNotFoundOnlyWhenAdapterLookupFails() {
		when(adapterRegistry.requireUpstream("UNKNOWN")).thenThrow(new AdapterNotFoundException("UPSTREAM", "UNKNOWN"));

		UpstreamCallbackDispatcher.DispatchResult result = dispatcher.dispatch("unknown", Collections.emptyMap())
			.block();

		assertThat(result.isUpstreamFound()).isFalse();
		assertThatThrownBy(result::getResponse).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void givesAdaptersAnImmutableSnapshotOfRequestParameters() {
		Map<String, String> parameters = new LinkedHashMap<String, String>();
		parameters.put("vendor_token", "token-1");
		when(adapterRegistry.requireUpstream("VENDOR")).thenReturn(adapter);
		when(callbackIngressService.accept(org.mockito.ArgumentMatchers.eq(adapter),
				org.mockito.ArgumentMatchers.anyMap()))
			.thenReturn(Mono.just(new CallbackIngressService.Result("event-1", "ACCEPTED")));
		when(adapter.callbackSuccess(org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.eq("event-1"),
				org.mockito.ArgumentMatchers.eq("ACCEPTED")))
			.thenReturn(Mono.just(new UpstreamCallbackResponse(200, Collections.emptyMap(), "OK")));

		dispatcher.dispatch("vendor", parameters).block();
		parameters.put("vendor_token", "changed-after-dispatch");

		ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
		verify(callbackIngressService).accept(org.mockito.ArgumentMatchers.eq(adapter), captor.capture());
		assertThat(captor.getValue()).containsEntry("vendor_token", "token-1");
		assertThatThrownBy(() -> captor.getValue().put("new_key", "new_value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
