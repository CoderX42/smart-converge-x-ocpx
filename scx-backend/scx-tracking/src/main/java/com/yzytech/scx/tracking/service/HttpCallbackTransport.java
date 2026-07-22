package com.yzytech.scx.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.tracking.config.TrackingProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpCallbackTransport implements CallbackTransport {

	private final ObjectMapper objectMapper;

	private final RestClient restClient;

	private final SecureRandom secureRandom = new SecureRandom();

	public HttpCallbackTransport(ObjectMapper objectMapper, TrackingProperties properties) {
		this.objectMapper = objectMapper;
		HttpClient httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofMillis(properties.getCallbackConnectTimeoutMs()))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(properties.getCallbackReadTimeoutMs()));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public void post(PostbackRequest request) {
		try {
			var response = restClient.post()
				.uri(request.endpoint())
				.contentType(MediaType.APPLICATION_JSON)
				.headers(headers -> addHeaders(headers, request))
				.body(request.payload())
				.retrieve()
				.toEntity(String.class);
			if (response.getStatusCode() != HttpStatus.OK)
				throw new CallbackDeliveryException(false, "HTTP_" + response.getStatusCode().value(),
						"回传目标返回 HTTP " + response.getStatusCode().value());
			if (response.getBody() == null
					|| objectMapper.readTree(response.getBody()).path("code").asInt(Integer.MIN_VALUE) != 0) {
				throw new CallbackDeliveryException(false, "PROTOCOL_REJECTED", "回传目标未返回 HTTP 200 与 code=0");
			}
		}
		catch (RestClientResponseException exception) {
			int status = exception.getStatusCode().value();
			boolean retryable = status == 408 || status == 429 || status >= 500;
			throw new CallbackDeliveryException(retryable, "HTTP_" + status, "回传目标返回 HTTP " + status);
		}
		catch (ResourceAccessException exception) {
			throw new CallbackDeliveryException(true, "NETWORK_ERROR", "回传目标连接失败");
		}
		catch (CallbackDeliveryException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new CallbackDeliveryException(true, "TRANSPORT_ERROR", "回传请求发送失败");
		}
		catch (Exception exception) {
			throw new CallbackDeliveryException(false, "PROTOCOL_INVALID", "回传目标响应格式无效");
		}
	}

	private void addHeaders(org.springframework.http.HttpHeaders headers, PostbackRequest request) {
		headers.set("Idempotency-Key", request.idempotencyKey());
		if (request.clientId() == null || request.keyVersion() == null || request.secret() == null)
			return;
		String timestamp = String.valueOf(System.currentTimeMillis());
		byte[] nonceBytes = new byte[18];
		secureRandom.nextBytes(nonceBytes);
		String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
		String path = URI.create(request.endpoint()).getRawPath();
		String signingInput = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n"
				+ SignUtils.sha256(request.payload());
		headers.set("X-Client-Id", request.clientId());
		headers.set("X-Key-Version", request.keyVersion());
		headers.set("X-Timestamp", timestamp);
		headers.set("X-Nonce", nonce);
		headers.set("X-Sign", SignUtils.hmacSha256(signingInput, request.secret()));
	}

}
