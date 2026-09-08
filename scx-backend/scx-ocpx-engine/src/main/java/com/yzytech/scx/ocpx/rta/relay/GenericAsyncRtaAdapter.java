package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class GenericAsyncRtaAdapter implements RtaUpstreamAdapter {

	public String code() {
		return "GENERIC_ASYNC";
	}

	public Outbound build(RtaState state, Request request, String callbackBase) {
		URI callback = URI.create(callbackBase + "/rta/callbacks/" + state.getUpstream().accessCode());
		if (!"https".equals(callback.getScheme()) || callback.getHost() == null)
			throw new RelayException(503, "RTA_CALLBACK_BASE_UNAVAILABLE");
		return new Outbound(state.getUpstream().endpoint(), state.getUpstream().credentialRef(), RelayJson
			.write(Map.of("requestId", state.getRequestId(), "relayRequestId", state.getRelayRequestId(), "candidates",
					state.getBindings().stream().map(b -> new Candidate(b.productId(), b.externalTaskId())).toList(),
					"device", request.device(), "context", request.context(), "callbackUrl", callback.toString())));
	}

	public Decision submitted(RtaState state, HttpResult response) {
		if (response.status() != 202)
			throw new RelayException(502, "RTA_UPSTREAM_NOT_ACCEPTED");
		return null;
	}

	public Decision callback(Upstream upstream, String path, String body, Map<String, String> headers,
			RelaySecurity security) {
		security.verify(upstream.credentialRef(), path, body, headers, System.currentTimeMillis());
		return RelayJson.read(body, Decision.class);
	}

}
