package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class CallbackTokenService {

	private final OcpxRuntimeProperties properties;

	public CallbackTokenService(OcpxRuntimeProperties properties) {
		this.properties = properties;
	}

	public String issue(String callbackRef) {
		long expiresAt = Instant.now().plus(properties.getCallbackTokenTtl()).getEpochSecond();
		String payload = callbackRef + "." + expiresAt;
		String encoded = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		return encoded + "." + OcpxHashing.hmacSha256(properties.getCallbackTokenKey(), encoded);
	}

	public String verify(String token) {
		if (token == null || token.isBlank()) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "ext is required");
		}
		int separator = token.lastIndexOf('.');
		if (separator < 1) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "invalid ext");
		}
		String encoded = token.substring(0, separator);
		String signature = token.substring(separator + 1);
		String expected = OcpxHashing.hmacSha256(properties.getCallbackTokenKey(), encoded);
		if (!OcpxHashing.constantTimeEquals(signature, expected)) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "invalid ext signature");
		}
		try {
			String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int delimiter = payload.lastIndexOf('.');
			String callbackRef = payload.substring(0, delimiter);
			long expiresAt = Long.parseLong(payload.substring(delimiter + 1));
			if (Instant.now().getEpochSecond() > expiresAt) {
				throw new OcpxException(HttpStatus.GONE, "ext expired");
			}
			return callbackRef;
		}
		catch (OcpxException e) {
			throw e;
		}
		catch (Exception e) {
			throw new OcpxException(HttpStatus.BAD_REQUEST, "invalid ext");
		}
	}

}
