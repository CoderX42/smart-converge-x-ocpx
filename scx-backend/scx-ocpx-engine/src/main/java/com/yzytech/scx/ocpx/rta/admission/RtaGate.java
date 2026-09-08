package com.yzytech.scx.ocpx.rta.admission;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.rta.relay.*;
import org.springframework.stereotype.Component;

/** Independent RTA request identity; never falls back to the event request ID. */
@Component
public class RtaGate {

	private final RelayRepository repository;

	private final RelayStore store;

	private final RelayAudit audit;

	public RtaGate(RelayRepository repository, RelayStore store, RelayAudit audit) {
		this.repository = repository;
		this.store = store;
		this.audit = audit;
	}

	public void requireAllowed(FeedbackReq request) {
		if (request == null || request.getEvent() == null
				|| !java.util.Set.of("IMP", "CLICK").contains(request.getEvent().name()))
			return;
		request.setRtaProtected(false);
		try {
			if (!repository.taskRequiresRta(request.getTId()))
				return;
			request.setRtaProtected(true);
			if (request.getRtaRequestId() == null || !request.getRtaRequestId().matches("[a-f0-9]{32}"))
				throw new RelayException(403, "RTA_ADMISSION_MISSING");
			var state = store.get(request.getRtaRequestId());
			try {
				if (!state.getMediaId().equals(request.getMId()) || !state.getStatus().equals("ALLOW")
						|| state.getExpiresAt() <= System.currentTimeMillis()
						|| !state.getMatchedTaskIds().contains(request.getTId()))
					throw new RelayException(403, "RTA_ADMISSION_DENIED");
				var binding = state.getBindings()
					.stream()
					.filter(b -> b.taskId().equals(request.getTId()))
					.findFirst()
					.orElseThrow();
				if (!binding.mediaId().equals(request.getMId()) || !binding.advertiserId().equals(request.getAId())
						|| !binding.linkId().equals(request.getLId()))
					throw new RelayException(403, "RTA_ROUTE_MISMATCH");
				repository.current(state, binding);
				audit.write(state, "EVENT_GATE", true, "ALLOW", 0);
			}
			catch (RuntimeException e) {
				audit.write(state, "EVENT_GATE", false, "RTA_ADMISSION_DENIED", 0);
				throw e;
			}
		}
		catch (RelayException e) {
			throw new RtaAdmissionDeniedException(e.getMessage());
		}
		catch (RuntimeException e) {
			throw new com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException("RTA_GATE_UNAVAILABLE", e);
		}
	}

}
