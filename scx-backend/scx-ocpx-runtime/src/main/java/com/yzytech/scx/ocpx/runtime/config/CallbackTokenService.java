package com.yzytech.scx.ocpx.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Stateless signed callback correlation; no callback-reference table is required. */
@Component
public class CallbackTokenService {

	private static final String PURPOSE = "OCPX_CALLBACK_V2";

	private final ObjectMapper objectMapper;
	private final ProtocolCrypto crypto;
	private final OcpxRuntimeProperties properties;

	public CallbackTokenService(ObjectMapper objectMapper, ProtocolCrypto crypto, OcpxRuntimeProperties properties) {
		this.objectMapper = objectMapper;
		this.crypto = crypto;
		this.properties = properties;
	}

	public String issue(String monitorTaskId, String sourceEventId, String clickId) {
		try {
			long expiresAt = Instant.now().plus(properties.getCallbackTokenTtl()).getEpochSecond();
			return crypto.signToken(PURPOSE,
					objectMapper.writeValueAsString(new Payload(monitorTaskId, sourceEventId, clickId, expiresAt)));
		}
		catch (Exception error) {
			throw new IllegalStateException("cannot create callback token", error);
		}
	}

	public Payload verify(String token) {
		try {
			Payload payload = objectMapper.readValue(crypto.verifyToken(PURPOSE, token), Payload.class);
			if (payload.monitorTaskId() == null || payload.monitorTaskId().isBlank()
					|| payload.sourceEventId() == null || payload.sourceEventId().isBlank()
					|| payload.expiresAt() < Instant.now().getEpochSecond()) {
				throw new IllegalArgumentException("callback token is missing required data or has expired");
			}
			return payload;
		}
		catch (IllegalArgumentException error) {
			throw error;
		}
		catch (Exception error) {
			throw new IllegalArgumentException("callback token is invalid", error);
		}
	}

	public record Payload(String monitorTaskId, String sourceEventId, String clickId, long expiresAt) {
	}

}
