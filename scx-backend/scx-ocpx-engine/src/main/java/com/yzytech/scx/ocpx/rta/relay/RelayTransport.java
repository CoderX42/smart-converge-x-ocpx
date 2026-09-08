package com.yzytech.scx.ocpx.rta.relay;

public interface RelayTransport {

	int post(String endpoint, String credentialRef, String body, long timeoutMillis);

	default RtaUpstreamAdapter.HttpResult exchange(RtaUpstreamAdapter.Outbound request, long timeoutMillis) {
		return new RtaUpstreamAdapter.HttpResult(
				post(request.endpoint(), request.credentialRef(), request.body(), timeoutMillis), "");
	}

}
