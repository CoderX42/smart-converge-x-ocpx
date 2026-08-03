package com.yzytech.scx.ocpx.runtime.security;

import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RequestAuthenticator {

	private static final DefaultRedisScript<String> REPLAY_AND_RATE_SCRIPT = new DefaultRedisScript<>("""
			if redis.call('EXISTS', KEYS[1]) == 1 then return 'NONCE_REPLAY' end
			redis.call('SET', KEYS[1], '1', 'PX', ARGV[1])
			local requests = redis.call('INCR', KEYS[2])
			if requests == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[2]) end
			if requests > tonumber(ARGV[3]) then return 'RATE_LIMIT' end
			return 'OK'
			""", String.class);

	private final StringRedisTemplate redisTemplate;

	private final OcpxRuntimeProperties properties;

	public RequestAuthenticator(StringRedisTemplate redisTemplate, OcpxRuntimeProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	public void authenticate(RouteSnapshot route, String method, String path, String rawBody, HttpHeaders headers) {
		if ("NONE".equalsIgnoreCase(route.channelAuthType())) {
			return;
		}
		String clientId = required(headers, "X-Client-Id");
		String keyVersion = required(headers, "X-Key-Version");
		String timestamp = required(headers, "X-Timestamp");
		String nonce = required(headers, "X-Nonce");
		String signature = required(headers, "X-Sign");
		if (nonce.length() < 16 || nonce.length() > 128 || !signature.matches("[a-fA-F0-9]{64}")) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "invalid nonce or signature format");
		}
		if (!route.channelCode().equals(clientId) || !Integer.toString(route.channelKeyVersion()).equals(keyVersion)) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "invalid client credential");
		}
		long timestampMillis;
		try {
			timestampMillis = Long.parseLong(timestamp);
			if (timestampMillis >= 0 && timestampMillis < 10_000_000_000L) {
				timestampMillis = Math.multiplyExact(timestampMillis, 1000);
			}
		}
		catch (RuntimeException e) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "invalid timestamp");
		}
		long now = Instant.now().toEpochMilli();
		long allowedSkew = properties.getNonceTtl().toMillis();
		if (timestampMillis < now - allowedSkew || timestampMillis > now + allowedSkew) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "request timestamp expired");
		}
		String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + OcpxHashing.sha256(rawBody);
		String expected = OcpxHashing.hmacSha256(route.channelSecret(), canonical);
		if (!OcpxHashing.constantTimeEquals(signature, expected)) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, "invalid signature");
		}
		try {
			String prefix = "scx:ocpx:{auth:" + clientId + "}";
			String result = redisTemplate.execute(REPLAY_AND_RATE_SCRIPT,
					List.of(prefix + ":nonce:" + nonce,
							prefix + ":qps:" + Instant.now().getEpochSecond()),
					Long.toString(properties.getNonceTtl().toMillis()), "2000",
					Long.toString(properties.getPerChannelQps()));
			if ("NONCE_REPLAY".equals(result)) {
				throw new OcpxException(HttpStatus.CONFLICT, "nonce replayed");
			}
			if ("RATE_LIMIT".equals(result)) {
				throw new OcpxException(HttpStatus.TOO_MANY_REQUESTS, "channel rate limit exceeded");
			}
			if (!"OK".equals(result)) {
				throw new OcpxException(HttpStatus.SERVICE_UNAVAILABLE, "request replay protection unavailable");
			}
		}
		catch (OcpxException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new OcpxException(HttpStatus.SERVICE_UNAVAILABLE, "request replay protection unavailable");
		}
	}

	private String required(HttpHeaders headers, String name) {
		String value = headers.getFirst(name);
		if (value == null || value.isBlank()) {
			throw new OcpxException(HttpStatus.UNAUTHORIZED, name + " is required");
		}
		return value;
	}

}
