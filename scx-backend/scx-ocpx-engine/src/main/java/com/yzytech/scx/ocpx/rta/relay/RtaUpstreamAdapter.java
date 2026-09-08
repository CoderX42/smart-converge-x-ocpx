package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import java.util.*;

public interface RtaUpstreamAdapter {

	String code();

	default RtaProtocols.Definition definition() {
		return RtaProtocols.require(code());
	}

	default void validate(Request request, Upstream upstream) {
		if (upstream.configSchemaVersion() != 1)
			throw new RelayException(400, "RTA_CONFIG_VERSION_UNSUPPORTED");
		definition().validateConfig(upstream.protocolConfig());
		if (request.taskIds().size() > definition().maxCandidates())
			throw new RelayException(400, "RTA_SINGLE_TASK_REQUIRED");
	}

	Outbound build(RtaState state, Request request, String callbackBase);

	/** null means accepted and awaiting an independent callback. */
	Decision submitted(RtaState state, HttpResult response);

	default Decision callback(Upstream upstream, String path, String body, Map<String, String> headers,
			RelaySecurity security) {
		throw new RelayException(404, "RTA_CALLBACK_UNSUPPORTED");
	}

	record Outbound(String endpoint, String credentialRef, String body, Set<String> queryKeys) {
		public Outbound(String endpoint, String credentialRef, String body) {
			this(endpoint, credentialRef, body, Set.of());
		}
	}

	record HttpResult(int status, String body) {
	}

}
