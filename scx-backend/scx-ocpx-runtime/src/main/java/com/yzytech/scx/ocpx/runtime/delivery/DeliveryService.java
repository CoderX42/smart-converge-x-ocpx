package com.yzytech.scx.ocpx.runtime.delivery;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

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

	private final ConcurrentHashMap<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

	public DeliveryService(OcpxPersistenceRepository repository, AdapterRegistry adapterRegistry,
			CallbackTokenService tokenService, DeliveryHttpClient httpClient, OcpxRuntimeProperties properties,
			DeliveryAuditSanitizer auditSanitizer) {
		this.repository = repository;
		this.adapterRegistry = adapterRegistry;
		this.tokenService = tokenService;
		this.httpClient = httpClient;
		this.properties = properties;
		this.auditSanitizer = auditSanitizer;
	}

	public void deliver(DeliveryJob job) {
		CircuitState circuit = circuits.computeIfAbsent(job.adapterCode(), ignored -> new CircuitState());
		if (circuit.isOpen()) {
			repository.markDeliveryFailure(job, null, "ADAPTER_CIRCUIT_OPEN");
			return;
		}
		Semaphore bulkhead = bulkheads.computeIfAbsent(job.adapterCode(),
				ignored -> new Semaphore(properties.getAdapterConcurrency()));
		if (!bulkhead.tryAcquire()) {
			repository.markDeliveryFailure(job, null, "ADAPTER_BACKPRESSURE");
			return;
		}
		try {
			DeliveryContext context = repository.loadDeliveryContext(job);
			boolean success;
			if ("FEEDBACK_UPSTREAM".equals(job.direction())) {
				success = deliverUpstream(context);
			}
			else if ("CALLBACK_DOWNSTREAM".equals(job.direction())) {
				success = deliverDownstream(context);
			}
			else {
				throw new IllegalArgumentException("unknown delivery direction");
			}
			if (success) {
				circuit.success();
			}
			else {
				circuit.failure(properties);
			}
		}
		catch (Exception e) {
			circuit.failure(properties);
			repository.markDeliveryFailure(job, null, safeError(e));
		}
		finally {
			bulkhead.release();
		}
	}

	private boolean deliverUpstream(DeliveryContext context) {
		UpstreamAdapter adapter = adapterRegistry.requireUpstream(context.job().adapterCode());
		String callbackUrl = UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
			.path("/open-api/ocpx/v1/upstreams/")
			.path(context.job().adapterCode().toLowerCase(Locale.ROOT))
			.path("/callback")
			.queryParam("ext", tokenService.issue(context.job().callbackRef()))
			.queryParam("s", context.route().channelId() + "_" + context.route().routeId())
			.build(true)
			.toUriString();
		AdapterHttpRequest request = adapter.buildFeedback(context.route(), context.feedback(), callbackUrl);
		long started = System.nanoTime();
		try {
			AdapterHttpResponse response = httpClient.exchange(request);
			DeliveryAttemptAudit audit = auditSanitizer.summarize(request, response, elapsedMs(started));
			if (response != null && adapter.isSuccess(response)) {
				repository.markDeliverySuccess(context.job(), response.statusCode(), audit);
				return true;
			}
			repository.markDeliveryFailure(context.job(), response == null ? null : response.statusCode(),
					adapter.failureReason(response), audit);
			return false;
		}
		catch (Exception error) {
			repository.markDeliveryFailure(context.job(), null, safeError(error),
					auditSanitizer.summarize(request, null, elapsedMs(started)));
			return false;
		}
	}

	private boolean deliverDownstream(DeliveryContext context) {
		DownstreamAdapter adapter = adapterRegistry.requireDownstream(context.job().adapterCode());
		CallbackReqV1 callback = new CallbackReqV1("1.0", context.callback().eventId(),
				context.callback().sourceEventId(), context.callback().eventType().name(),
				context.callback().actionTime(), context.callback().amount(), context.callback().amountRaw(),
				context.feedback().getClickid(), context.route().routeId(), context.route().channelId(),
				Map.of("callback_ref", context.job().callbackRef()));
		AdapterHttpRequest request = adapter.buildCallback(context.route(), callback);
		long started = System.nanoTime();
		try {
			AdapterHttpResponse response = httpClient.exchange(request);
			DeliveryAttemptAudit audit = auditSanitizer.summarize(request, response, elapsedMs(started));
			if (response != null && adapter.isSuccess(response)) {
				repository.markDeliverySuccess(context.job(), response.statusCode(), audit);
				return true;
			}
			repository.markDeliveryFailure(context.job(), response == null ? null : response.statusCode(),
					"DOWNSTREAM_REJECTED", audit);
			return false;
		}
		catch (Exception error) {
			repository.markDeliveryFailure(context.job(), null, safeError(error),
					auditSanitizer.summarize(request, null, elapsedMs(started)));
			return false;
		}
	}

	private String safeError(Exception error) {
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
