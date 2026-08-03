package com.yzytech.scx.ocpx.runtime.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;

@SuppressWarnings("unchecked")
class RequestAuthenticatorTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

	private final OcpxRuntimeProperties properties = new OcpxRuntimeProperties();

	private RequestAuthenticator authenticator;

	@BeforeEach
	void setUp() {
		properties.setNonceTtl(Duration.ofMinutes(5));
		properties.setPerChannelQps(100);
		doReturn("OK").when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
		authenticator = new RequestAuthenticator(redisTemplate, properties);
	}

	@Test
	void acceptsValidSignatureAndRejectsNonceReplay() {
		String body = "{\"event\":\"click\"}";
		String path = "/rtb/open-api/ocpx/v1/channels/demo/feedback";
		String timestamp = Long.toString(System.currentTimeMillis());
		String nonce = "7858c055c6754ee688873e11b9c17088";
		HttpHeaders headers = signedHeaders(body, path, timestamp, nonce);

		assertThatCode(() -> authenticator.authenticate(route(), "POST", path, body, headers))
			.doesNotThrowAnyException();

		doReturn("NONCE_REPLAY").when(redisTemplate)
			.execute(any(RedisScript.class), anyList(), any(Object[].class));
		assertThatThrownBy(() -> authenticator.authenticate(route(), "POST", path, body, headers))
			.isInstanceOfSatisfying(OcpxException.class,
					error -> org.assertj.core.api.Assertions.assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void rejectsTamperedBodyBeforeConsumingNonce() {
		String path = "/rtb/open-api/ocpx/v1/channels/demo/feedback";
		String timestamp = Long.toString(System.currentTimeMillis());
		HttpHeaders headers = signedHeaders("{\"event\":\"click\"}", path, timestamp,
				"7858c055c6754ee688873e11b9c17088");

		assertThatThrownBy(
				() -> authenticator.authenticate(route(), "POST", path, "{\"event\":\"show\"}", headers))
			.isInstanceOfSatisfying(OcpxException.class,
					error -> org.assertj.core.api.Assertions.assertThat(error.getStatus())
					.isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void failsClosedWhenReplayProtectionIsUnavailable() {
		String body = "{\"event\":\"click\"}";
		String path = "/rtb/open-api/ocpx/v1/channels/demo/feedback";
		String timestamp = Long.toString(System.currentTimeMillis());
		HttpHeaders headers = signedHeaders(body, path, timestamp, "7858c055c6754ee688873e11b9c17088");
		doThrow(new IllegalStateException("redis unavailable")).when(redisTemplate)
			.execute(any(RedisScript.class), anyList(), any(Object[].class));

		assertThatThrownBy(() -> authenticator.authenticate(route(), "POST", path, body, headers))
			.isInstanceOfSatisfying(OcpxException.class,
					error -> org.assertj.core.api.Assertions.assertThat(error.getStatus())
					.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
	}

	@Test
	void returnsTooManyRequestsWhenAtomicChannelLimitIsExceeded() {
		String body = "{\"event\":\"click\"}";
		String path = "/rtb/open-api/ocpx/v1/channels/demo/feedback";
		String timestamp = Long.toString(System.currentTimeMillis());
		HttpHeaders headers = signedHeaders(body, path, timestamp, "7858c055c6754ee688873e11b9c17088");
		doReturn("RATE_LIMIT").when(redisTemplate)
			.execute(any(RedisScript.class), anyList(), any(Object[].class));

		assertThatThrownBy(() -> authenticator.authenticate(route(), "POST", path, body, headers))
			.isInstanceOfSatisfying(OcpxException.class,
					error -> org.assertj.core.api.Assertions.assertThat(error.getStatus())
					.isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
	}

	private HttpHeaders signedHeaders(String body, String path, String timestamp, String nonce) {
		String canonical = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + OcpxHashing.sha256(body);
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Client-Id", "demo");
		headers.set("X-Key-Version", "1");
		headers.set("X-Timestamp", timestamp);
		headers.set("X-Nonce", nonce);
		headers.set("X-Sign", OcpxHashing.hmacSha256("channel-secret-at-least-32-bytes", canonical));
		return headers;
	}

	private RouteSnapshot route() {
		return new RouteSnapshot("route-1", 1, "channel-1", "demo", "STANDARD", "HMAC", 1,
				"channel-secret-at-least-32-bytes", "advertiser-1", "app-1", "COCO", null,
				"https://coco.example/click?callback=__CALLBACK__", null, "https://downstream.example/callback",
				BigDecimal.ONE, "OFF", "OFF");
	}

}
