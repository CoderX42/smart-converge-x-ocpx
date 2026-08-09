package com.yzytech.scx.ocpx.runtime.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.callback.CallbackIngressService;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationRepository;
import com.yzytech.scx.ocpx.runtime.ingress.FeedbackIngressService;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalApiDebugServiceTest {

	@Test
	void acceptsOneSignedGrantAndRejectsReplay() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		String method = "POST";
		String path = "/internal/api-debug/executions";
		String body = "{\"executionId\":\"debug-1\"}";
		String timestamp = Long.toString(System.currentTimeMillis());
		String nonce = "one-time-nonce";
		String ephemeralKey = "one-time-ephemeral-key";
		String bodyHash = sha256(body);
		String storedGrant = sha256(ephemeralKey) + "|" + method + "|" + path + "|" + bodyHash + "|" + timestamp;
		when(values.getAndDelete("scx:api-debug:internal:" + nonce)).thenReturn(storedGrant, null);

		String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-SCX-Debug-Timestamp", timestamp);
		headers.set("X-SCX-Debug-Nonce", nonce);
		headers.set("X-SCX-Debug-Key", ephemeralKey);
		headers.set("X-SCX-Debug-Sign", hmac(ephemeralKey, canonical));

		InternalApiDebugService service = service(redis);
		service.authenticate(method, path, body, headers);

		assertThatThrownBy(() -> service.authenticate(method, path, body, headers))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("replayed");
	}

	private InternalApiDebugService service(StringRedisTemplate redis) {
		return new InternalApiDebugService(redis, new ObjectMapper(), mock(JdbcTemplate.class),
				mock(OcpxRuntimeProperties.class), mock(RouteConfigurationRepository.class),
				mock(FeedbackIngressService.class), mock(CallbackIngressService.class),
				mock(OcpxPersistenceRepository.class), mock(CallbackTokenService.class), mock(AdapterRegistry.class));
	}

	private static String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(value)));
	}

	private static String hmac(String key, String value) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(bytes(key), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(bytes(value)));
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

}
