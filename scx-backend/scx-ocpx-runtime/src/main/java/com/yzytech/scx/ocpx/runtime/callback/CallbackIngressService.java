package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import com.yzytech.scx.ocpx.runtime.deduction.DeductionService;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import com.yzytech.scx.ocpx.runtime.support.ReactiveBlockingExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
		return accept(adapter, parameters, null);
	}

	public Mono<Result> accept(UpstreamAdapter adapter, Map<String, String> parameters, String debugExecutionId) {
		return adapter.callbackToken(parameters)
			.switchIfEmpty(Mono.error(new IllegalArgumentException("callback token is required")))
			.flatMap(token -> blockingExecutor.call(() -> tokenService.verify(token)))
			.flatMap(callbackRef -> blockingExecutor.call(() -> repository.requireCallbackContext(callbackRef))
				.flatMap(context -> parseAndPersist(adapter, parameters, callbackRef,
						debugExecutionId == null ? context
								: new CallbackContext(context.callbackRef(), context.feedbackEventId(), context.route(),
										context.feedback(), debugExecutionId))))
			.onErrorMap(IllegalArgumentException.class,
					error -> new OcpxException(HttpStatus.BAD_REQUEST, error.getMessage()));
	}

	private Mono<Result> parseAndPersist(UpstreamAdapter adapter, Map<String, String> parameters, String callbackRef,
			CallbackContext context) {
		if (!adapter.code().equalsIgnoreCase(context.route().upstreamAdapterCode())) {
			return Mono.error(new OcpxException(HttpStatus.BAD_REQUEST, "callback upstream does not match route"));
		}
		return adapter.parseCallback(callbackRef, parameters)
			.map(parsed -> mapEvent(parsed, context))
			.flatMap(callback -> deductionService.decide(callback.eventId(), context.route(), callback.eventType())
				.flatMap(decision -> blockingExecutor
					.call(() -> repository.persistCallback(callback, context, decision.forwarded(), decision.reason()))
					.map(result -> toResult(callback.eventId(), result))));
	}

	private NormalizedCallbackEvent mapEvent(NormalizedCallbackEvent parsed, CallbackContext context) {
		String rawCode = parsed.sourceEventCode();
		if (context.route().eventSnapshotVersion() == 0) {
			String legacyCode = parsed.eventType().name();
			RouteEventSnapshot legacyMapping = new RouteEventSnapshot("LEGACY", legacyCode, legacyCode, legacyCode,
					legacyCode, legacyCode, legacyCode, BigDecimal.ZERO, 0);
			return parsed.mapped(context.feedbackEventId(), parsed.eventType(), legacyMapping);
		}
		RouteEventSnapshot mapping = context.route().eventMappings()
			.stream()
			.filter(item -> item.upstreamEventCode().equalsIgnoreCase(rawCode)
					|| item.upstreamEventCode().equalsIgnoreCase(parsed.eventType().name()))
			.findFirst()
			.orElseThrow(() -> new OcpxException(HttpStatus.UNPROCESSABLE_ENTITY,
					"event is not mapped for the click route version"));
		return parsed.mapped(context.feedbackEventId(), OcpxEventType.fromConversionCode(mapping.conversionEventCode()),
				mapping);
	}

	private Result toResult(String eventId, OcpxPersistenceRepository.CallbackPersistenceResult result) {
		return switch (result.status()) {
			case "DUPLICATE" -> new Result(eventId, "DUPLICATE");
			case "LIMIT_EXCEEDED" -> throw new OcpxException(HttpStatus.TOO_MANY_REQUESTS,
					"daily callback limit exceeded; retry after next Asia/Shanghai natural day");
			case "INSUFFICIENT_BALANCE" -> throw new OcpxException(HttpStatus.PAYMENT_REQUIRED,
					"advertiser balance is insufficient; recharge and retry the same event");
			default -> new Result(eventId, "ACCEPTED");
		};
	}

	public record Result(String eventId, String status) {
	}

}
