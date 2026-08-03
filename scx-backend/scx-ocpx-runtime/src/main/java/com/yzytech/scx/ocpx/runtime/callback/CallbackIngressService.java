package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.deduction.DeductionService;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class CallbackIngressService {

	private final CallbackTokenService tokenService;

	private final AdapterRegistry adapterRegistry;

	private final OcpxPersistenceRepository repository;

	private final DeductionService deductionService;

	public CallbackIngressService(CallbackTokenService tokenService, AdapterRegistry adapterRegistry,
			OcpxPersistenceRepository repository, DeductionService deductionService) {
		this.tokenService = tokenService;
		this.adapterRegistry = adapterRegistry;
		this.repository = repository;
		this.deductionService = deductionService;
	}

	public Result accept(String upstreamCode, Map<String, String> parameters) {
		try {
			String normalizedUpstreamCode = upstreamCode.toUpperCase(Locale.ROOT);
			String callbackRef = tokenService.verify(parameters.get("ext"));
			CallbackContext context = repository.requireCallbackContext(callbackRef);
			if (!normalizedUpstreamCode.equals(context.route().upstreamAdapterCode())) {
				throw new OcpxException(HttpStatus.BAD_REQUEST, "callback upstream does not match route");
			}
			UpstreamAdapter adapter = adapterRegistry.requireUpstream(normalizedUpstreamCode);
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
