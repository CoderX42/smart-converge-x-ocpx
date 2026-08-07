package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.runtime.deduction.DeductionService;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CallbackIngressService {

	private final CallbackTokenService tokenService;

	private final OcpxPersistenceRepository repository;

	private final DeductionService deductionService;

	public CallbackIngressService(CallbackTokenService tokenService, OcpxPersistenceRepository repository,
			DeductionService deductionService) {
		this.tokenService = tokenService;
		this.repository = repository;
		this.deductionService = deductionService;
	}

	public Result accept(UpstreamAdapter adapter, Map<String, String> parameters) {
		try {
			String callbackRef = tokenService.verify(adapter.callbackToken(parameters));
			CallbackContext context = repository.requireCallbackContext(callbackRef);
			if (!adapter.code().equalsIgnoreCase(context.route().upstreamAdapterCode())) {
				throw new OcpxException(HttpStatus.BAD_REQUEST, "callback upstream does not match route");
			}
			NormalizedCallbackEvent parsed = adapter.parseCallback(callbackRef, parameters);
			NormalizedCallbackEvent callback = new NormalizedCallbackEvent(parsed.eventId(), context.feedbackEventId(),
					parsed.callbackRef(), parsed.eventType(), parsed.actionTime(), parsed.amount(), parsed.amountRaw());
			DeductionService.Decision decision = deductionService.decide(callback.eventId(), context.route(),
					callback.eventType());
			boolean inserted = repository.persistCallback(callback, context, decision.forwarded(), decision.reason());
			return new Result(callback.eventId(), inserted ? "ACCEPTED" : "DUPLICATE");
		}
		catch (OcpxException e) {
			throw e;
		}
		catch (IllegalArgumentException e) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	public record Result(String eventId, String status) {
	}

}
