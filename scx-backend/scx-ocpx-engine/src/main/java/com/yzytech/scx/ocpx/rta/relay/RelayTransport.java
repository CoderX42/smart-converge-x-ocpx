package com.yzytech.scx.ocpx.rta.relay;

public interface RelayTransport {

	int post(String endpoint, String credentialRef, String body, long timeoutMillis);

}
