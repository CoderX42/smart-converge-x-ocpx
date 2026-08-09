package com.yzytech.scx.ocpx.runtime.delivery;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.CallbackReqV1;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.model.DeliveryContext;
import com.yzytech.scx.ocpx.runtime.model.DeliveryAttemptAudit;
import com.yzytech.scx.ocpx.runtime.model.DeliveryJob;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DeliveryService {

	private final OcpxPersistenceRepository repository;

	private final AdapterRegistry adapterRegistry;

	private final CallbackTokenService tokenService;

	private final DeliveryHttpClient httpClient;

	private final OcpxRuntimeProperties properties;

	private final DeliveryAuditSanitizer auditSanitizer;

	private final ReactiveBlockingExecutor blockingExecutor;

	private final ConcurrentHashMap<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

	public DeliveryService(OcpxPersistenceRepository repository, AdapterRegistry adapterRegistry,
			CallbackTokenService tokenService, DeliveryHttpClient httpClient, OcpxRuntimeProperties properties,
			DeliveryAuditSanitizer auditSanitizer, ReactiveBlockingExecutor blockingExecutor) {
		this.repository = repository;
		this.adapterRegistry = adapterRegistry;
		this.tokenService = tokenService;
		this.httpClient = httpClient;
		this.properties = properties;
		this.auditSanitizer = auditSanitizer;
		this.blockingExecutor = blockingExecutor;
	}

	/**
	 * 执行一个可靠投递任务。只有后台 Worker 订阅该 Mono；Service 自己不调用 subscribe/block。
	 */
	public Mono<Void> deliver(DeliveryJob job) {
		return Mono.defer(() -> {
			CircuitState circuit = circuits.computeIfAbsent(job.adapterCode(), ignored -> new CircuitState());
			if (circuit.isOpen()) {
				return blockingExecutor.run(() -> repository.markDeliveryFailure(job, null, "ADAPTER_CIRCUIT_OPEN"));
			}
			Semaphore bulkhead = bulkheads.computeIfAbsent(job.adapterCode(),
					ignored -> new Semaphore(properties.getAdapterConcurrency()));
			if (!bulkhead.tryAcquire()) {
				return blockingExecutor.run(() -> repository.markDeliveryFailure(job, null, "ADAPTER_BACKPRESSURE"));
			}
			return blockingExecutor.call(() -> repository.loadDeliveryContext(job))
				.flatMap(this::deliverByDirection)
				.doOnNext(success -> {
					if (success) {
						circuit.success();
					}
					else {
						circuit.failure(properties);
					}
				})
				.onErrorResume(error -> {
					circuit.failure(properties);
					return blockingExecutor.run(() -> repository.markDeliveryFailure(job, null, safeError(error)))
						.thenReturn(false);
				})
				.then()
				.doFinally(ignored -> bulkhead.release());
		});
	}

	private Mono<Boolean> deliverByDirection(DeliveryContext context) {
		if ("FEEDBACK_UPSTREAM".equals(context.job().direction())) {
			return deliverUpstream(context);
		}
		if ("CALLBACK_DOWNSTREAM".equals(context.job().direction())) {
			return deliverDownstream(context);
		}
		return Mono.error(new IllegalArgumentException("unknown delivery direction"));
	}

	private Mono<Boolean> deliverUpstream(DeliveryContext context) {
		UpstreamAdapter adapter = adapterRegistry.requireUpstream(context.job().adapterCode());
		String callbackUrl = UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
			.path("/open-api/ocpx/v1/upstreams/")
			.path(context.job().adapterCode().toLowerCase(Locale.ROOT))
			.path("/callback")
			.queryParam("ext", tokenService.issue(context.job().callbackRef()))
			.queryParam("s", context.route().channelId() + "_" + context.route().routeId())
			.build(true)
			.toUriString();
		return adapter.buildFeedback(context.route(), context.feedback(), callbackUrl)
			.flatMap(request -> exchangeUpstream(context, adapter, request));
	}

	private Mono<Boolean> exchangeUpstream(DeliveryContext context, UpstreamAdapter adapter,
			AdapterHttpRequest request) {
		long started = System.nanoTime();
		return httpClient.exchange(request)
			.flatMap(response -> adapter.evaluateFeedbackResponse(response)
				.flatMap(result -> persistResult(context, request, response, result, started)))
			.onErrorResume(error -> persistTransportFailure(context, request, error, started));
	}

	private Mono<Boolean> deliverDownstream(DeliveryContext context) {
		DownstreamAdapter adapter = adapterRegistry.requireDownstream(context.job().adapterCode());
		CallbackReqV1 callback = new CallbackReqV1("1.0", context.callback().eventId(),
				context.callback().sourceEventId(), context.callback().mediaEventCode(),
				context.callback().actionTime(), context.callback().amount(), context.callback().amountRaw(),
				context.feedback().getClickid(), context.route().routeId(), context.route().channelId(),
				Map.of("callback_ref", context.job().callbackRef()));
		return adapter.buildCallback(context.route(), callback).flatMap(request -> {
			long started = System.nanoTime();
			return httpClient.exchange(request)
				.flatMap(response -> adapter.evaluateCallbackResponse(response)
					.flatMap(result -> persistResult(context, request, response, result, started)))
				.onErrorResume(error -> persistTransportFailure(context, request, error, started));
		});
	}

	private Mono<Boolean> persistResult(DeliveryContext context, AdapterHttpRequest request,
			AdapterHttpResponse response, AdapterDeliveryResult result, long started) {
		DeliveryAttemptAudit audit = auditSanitizer.summarize(request, response, elapsedMs(started));
		return blockingExecutor.run(() -> {
			if (result.success()) {
				repository.markDeliverySuccess(context.job(), response.statusCode(), audit);
			}
			else {
				repository.markDeliveryFailure(context.job(), response.statusCode(), result.reason(), audit);
			}
		}).thenReturn(result.success());
	}

	private Mono<Boolean> persistTransportFailure(DeliveryContext context, AdapterHttpRequest request, Throwable error,
			long started) {
		DeliveryAttemptAudit audit = auditSanitizer.summarize(request, null, elapsedMs(started));
		return blockingExecutor.run(() -> repository.markDeliveryFailure(context.job(), null, safeError(error), audit))
			.thenReturn(false);
	}

	private String safeError(Throwable error) {
		return error.getClass().getSimpleName();
	}

	private long elapsedMs(long startedNanos) {
		return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
	}

	private static final class CircuitState {

		private final AtomicInteger failures = new AtomicInteger();

		private volatile long openUntil;

		boolean isOpen() {
			return System.currentTimeMillis() < openUntil;
		}

		void success() {
			failures.set(0);
			openUntil = 0;
		}

		void failure(OcpxRuntimeProperties properties) {
			if (failures.incrementAndGet() >= properties.getCircuitFailureThreshold()) {
				openUntil = System.currentTimeMillis() + properties.getCircuitOpenDuration().toMillis();
				failures.set(0);
			}
		}

	}

}
