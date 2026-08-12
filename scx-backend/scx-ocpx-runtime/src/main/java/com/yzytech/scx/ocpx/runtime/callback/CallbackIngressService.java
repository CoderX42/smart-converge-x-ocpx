package com.yzytech.scx.ocpx.runtime.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.CallbackReqV2;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.DownstreamDeliveryContext;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxPartnerContext;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.config.CallbackTokenService;
import com.yzytech.scx.ocpx.runtime.config.PartnerContextRepository;
import com.yzytech.scx.ocpx.runtime.config.PartnerCredentialResolver;
import com.yzytech.scx.ocpx.runtime.delivery.DeliveryHttpClient;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxFlowLogRepository;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.DateTimeException;
import java.util.Map;
import java.util.TreeMap;

/** Synchronous upstream Callback -> downstream media forwarding. */
@Service
public class CallbackIngressService {

	private final ObjectMapper objectMapper;
	private final PartnerContextRepository contexts;
	private final CallbackTokenService callbackTokens;
	private final PartnerCredentialResolver credentials;
	private final AdapterRegistry adapters;
	private final DeliveryHttpClient httpClient;
	private final OcpxFlowLogRepository flowLogs;
	private final ReactiveBlockingExecutor blockingExecutor;

	public CallbackIngressService(ObjectMapper objectMapper, PartnerContextRepository contexts,
			CallbackTokenService callbackTokens, PartnerCredentialResolver credentials, AdapterRegistry adapters,
			DeliveryHttpClient httpClient, OcpxFlowLogRepository flowLogs,
			ReactiveBlockingExecutor blockingExecutor) {
		this.objectMapper = objectMapper;
		this.contexts = contexts;
		this.callbackTokens = callbackTokens;
		this.credentials = credentials;
		this.adapters = adapters;
		this.httpClient = httpClient;
		this.flowLogs = flowLogs;
		this.blockingExecutor = blockingExecutor;
	}

	public Mono<Result> accept(String advertiserCode, UpstreamAdapter adapter, Map<String, String> parameters) {
		return adapter.callbackToken(parameters)
			.switchIfEmpty(Mono.error(new OcpxException(HttpStatus.BAD_REQUEST, "callback reference is required")))
			.flatMap(token -> prepare(advertiserCode, adapter, token, parameters))
			.flatMap(this::deliver)
			.onErrorMap(IllegalArgumentException.class,
					error -> new OcpxException(HttpStatus.BAD_REQUEST, error.getMessage()))
			.onErrorMap(DateTimeException.class,
					error -> new OcpxException(HttpStatus.BAD_REQUEST, "callback time is invalid"))
			.onErrorMap(IllegalStateException.class,
					error -> new OcpxException(HttpStatus.SERVICE_UNAVAILABLE,
							"partner configuration is unavailable"));
	}

	private Mono<Work> prepare(String advertiserCode, UpstreamAdapter adapter, String token,
			Map<String, String> parameters) {
		return blockingExecutor.call(() -> {
			CallbackTokenService.Payload payload = callbackTokens.verify(token);
			OcpxPartnerContext context = contexts.findForCallback(advertiserCode, payload.monitorTaskId())
				.orElseThrow(() -> new OcpxException(HttpStatus.NOT_FOUND, "active monitor task not found"));
			if (!adapter.code().equalsIgnoreCase(context.upstreamAdapterCode())) {
				throw new OcpxException(HttpStatus.BAD_REQUEST, "callback advertiser does not match monitor task");
			}
			if (!StringUtils.hasText(context.downstreamCallbackUrl())) {
				throw new OcpxException(HttpStatus.UNPROCESSABLE_ENTITY, "media callback URL is not configured");
			}
			return new Prepared(context, payload);
		}).flatMap(prepared -> adapter.parseCallback(token, parameters).map(event -> build(prepared, event, parameters)));
	}

	private Work build(Prepared prepared, NormalizedCallbackEvent event, Map<String, String> parameters) {
		OcpxPartnerContext context = prepared.context();
		DownstreamAdapter downstream = adapters.requireDownstream(context.downstreamAdapterCode());
		PartnerCredentialResolver.ResolvedCredential credential = credentials.requireMedia(context.mediaAuthType(),
				context.mediaCredentialConfigKey(), context.mediaCredentialVersion());
		CallbackReqV2 callback = new CallbackReqV2("2.0", event.eventId(), prepared.payload().sourceEventId(),
				event.sourceEventCode(), event.actionTime(), event.amount(), event.amountRaw(),
				prepared.payload().clickId(), context.monitorTaskId(), Map.of());
		AdapterHttpRequest request = downstream
			.buildCallback(new DownstreamDeliveryContext(context, callback, credential.account(), credential.secret()));
		String payloadHash;
		try {
			payloadHash = OcpxHashing.sha256(objectMapper.writeValueAsString(new TreeMap<>(parameters)));
		}
		catch (Exception error) {
			throw new IllegalArgumentException("callback parameters cannot be normalized", error);
		}
		return new Work(context, callback, downstream, request, payloadHash, System.nanoTime());
	}

	private Mono<Result> deliver(Work work) {
		return httpClient.exchange(work.request()).onErrorResume(error -> log(work, "FAILED", null,
				safeMessage(error)).then(Mono.error(new OcpxException(HttpStatus.BAD_GATEWAY,
				"downstream callback delivery failed")))).flatMap(response -> {
			AdapterDeliveryResult result = work.adapter().evaluateCallbackResponse(response);
			String message = result.success() ? "partner accepted" : result.reason();
			return log(work, result.success() ? "SUCCESS" : "FAILED", response.statusCode(), message)
				.then(result.success() ? Mono.just(new Result(work.callback().eventId(), "ACCEPTED"))
						: Mono.error(new OcpxException(HttpStatus.BAD_GATEWAY,
								"downstream rejected callback: " + result.reason())));
		});
	}

	private Mono<Void> log(Work work, String status, Integer partnerHttpStatus, String message) {
		long duration = Math.max(0, (System.nanoTime() - work.started()) / 1_000_000);
		OffsetDateTime eventTime = work.callback().actionTime() == null ? OffsetDateTime.now(ZoneOffset.UTC)
				: work.callback().actionTime();
		return blockingExecutor.run(() -> flowLogs.record(work.context(), "CALLBACK", work.callback().eventId(),
				work.callback().sourceEventId(), work.callback().event(), work.payloadHash(), status, partnerHttpStatus,
				message, duration, eventTime));
	}

	private String safeMessage(Throwable error) {
		return "transport error: " + error.getClass().getSimpleName();
	}

	private record Prepared(OcpxPartnerContext context, CallbackTokenService.Payload payload) {
	}

	private record Work(OcpxPartnerContext context, CallbackReqV2 callback, DownstreamAdapter adapter,
			AdapterHttpRequest request, String payloadHash, long started) {
	}

	public record Result(String eventId, String status) {
	}

}
