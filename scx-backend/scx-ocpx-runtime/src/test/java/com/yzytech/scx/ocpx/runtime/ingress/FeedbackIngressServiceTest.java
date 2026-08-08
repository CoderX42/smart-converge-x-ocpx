package com.yzytech.scx.ocpx.runtime.ingress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationCache;
import com.yzytech.scx.ocpx.runtime.model.FeedbackEnvelope;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import com.yzytech.scx.ocpx.runtime.security.RequestAuthenticator;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackIngressServiceTest {

	@Test
	void generatedEventIdIsIncludedInTheEncryptedForwardingPayload() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		RouteConfigurationCache routeCache = mock(RouteConfigurationCache.class);
		RequestAuthenticator authenticator = mock(RequestAuthenticator.class);
		ProtocolCrypto protocolCrypto = mock(ProtocolCrypto.class);
		RedisFeedbackIngress redisIngress = mock(RedisFeedbackIngress.class);
		AdapterRegistry adapterRegistry = mock(AdapterRegistry.class);
		DownstreamAdapter downstream = mock(DownstreamAdapter.class);
		UpstreamAdapter upstream = mock(UpstreamAdapter.class);
		OcpxRuntimeProperties properties = new OcpxRuntimeProperties();
		FeedbackReqV1 feedback = new FeedbackReqV1();
		feedback.setEvent("show");
		feedback.setMonitorId("monitor-1");
		feedback.setEventTime(1_785_744_000L);
		RouteSnapshot route = route();

		when(routeCache.requireDownstreamAdapter("channel")).thenReturn("STANDARD");
		when(adapterRegistry.requireDownstream("STANDARD")).thenReturn(downstream);
		when(downstream.parseFeedback("{}")).thenReturn(Mono.just(feedback));
		when(routeCache.require("channel", "monitor-1")).thenReturn(route);
		when(adapterRegistry.requireUpstream("TEMPLATE_GET")).thenReturn(upstream);
		when(upstream.supports(OcpxEventType.SHOW)).thenReturn(true);
		when(upstream.validateFeedback(route, feedback)).thenReturn(Mono.empty());
		when(protocolCrypto.encrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(redisIngress.enqueue(any()))
			.thenReturn(new IngressResult("accepted", IngressResult.Status.ACCEPTED, OffsetDateTime.now()));
		FeedbackIngressService service = new FeedbackIngressService(objectMapper, routeCache, authenticator,
				protocolCrypto, redisIngress, adapterRegistry, properties, new ReactiveBlockingExecutor());

		service.accept("channel", "POST", "/ocpx/open-api/ocpx/v1/channels/channel/feedback", "{}", HttpHeaders.EMPTY)
			.block();

		ArgumentCaptor<FeedbackEnvelope> envelope = ArgumentCaptor.forClass(FeedbackEnvelope.class);
		verify(redisIngress).enqueue(envelope.capture());
		FeedbackReqV1 forwarded = objectMapper.readValue(envelope.getValue().encryptedFeedback(), FeedbackReqV1.class);
		assertThat(forwarded.getEventId()).isEqualTo(envelope.getValue().eventId()).isNotBlank();
	}

	private RouteSnapshot route() {
		return new RouteSnapshot("monitor-1", "biz-1", 1, "channel-1", "channel", "STANDARD", "HMAC", 1,
				"channel-secret-at-least-32-bytes", "advertiser-1", "app-1", "TEMPLATE_GET", null,
				"https://upstream.example/feedback?event_id=__EVENT_ID__&callback=__CALLBACK__",
				"__EVENT_ID__,__CALLBACK__", "https://downstream.example/callback", BigDecimal.ONE, BigDecimal.ONE,
				"OFF", "OFF");
	}

}
