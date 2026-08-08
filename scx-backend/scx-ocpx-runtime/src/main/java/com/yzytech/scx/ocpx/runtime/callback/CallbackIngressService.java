package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.runtime.deduction.DeductionService;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class CallbackIngressService {

	private final CallbackTokenService tokenService;

	private final OcpxPersistenceRepository repository;

	private final DeductionService deductionService;

	private final ReactiveBlockingExecutor blockingExecutor;

	public CallbackIngressService(CallbackTokenService tokenService, OcpxPersistenceRepository repository,
			DeductionService deductionService, ReactiveBlockingExecutor blockingExecutor) {
		this.tokenService = tokenService;
		this.repository = repository;
		this.deductionService = deductionService;
		this.blockingExecutor = blockingExecutor;
	}

	/** 上游 callback 的校验、归一化、扣量与可靠落库响应式编排。 */
	public Mono<Result> accept(UpstreamAdapter adapter, Map<String, String> parameters) {
		return adapter.callbackToken(parameters)
			.switchIfEmpty(Mono.error(new IllegalArgumentException("callback token is required")))
			.flatMap(token -> blockingExecutor.call(() -> tokenService.verify(token)))
			.flatMap(callbackRef -> blockingExecutor.call(() -> repository.requireCallbackContext(callbackRef))
				.flatMap(context -> parseAndPersist(adapter, parameters, callbackRef, context)))
			.onErrorMap(IllegalArgumentException.class,
					error -> new OcpxException(HttpStatus.BAD_REQUEST, error.getMessage()));
	}

	private Mono<Result> parseAndPersist(UpstreamAdapter adapter, Map<String, String> parameters, String callbackRef,
			CallbackContext context) {
		if (!adapter.code().equalsIgnoreCase(context.route().upstreamAdapterCode())) {
			return Mono.error(new OcpxException(HttpStatus.BAD_REQUEST, "callback upstream does not match route"));
		}
		return adapter.parseCallback(callbackRef, parameters)
			.map(parsed -> new NormalizedCallbackEvent(parsed.eventId(), context.feedbackEventId(),
					parsed.callbackRef(), parsed.eventType(), parsed.actionTime(), parsed.amount(), parsed.amountRaw()))
			.flatMap(callback -> deductionService.decide(callback.eventId(), context.route(), callback.eventType())
				.flatMap(decision -> blockingExecutor
					.call(() -> repository.persistCallback(callback, context, decision.forwarded(), decision.reason()))
					.map(inserted -> new Result(callback.eventId(), inserted ? "ACCEPTED" : "DUPLICATE"))));
	}

	public record Result(String eventId, String status) {
	}

}
