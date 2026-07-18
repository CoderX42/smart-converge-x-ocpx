package com.yzytech.scx.tracking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class HttpCallbackTransport implements CallbackTransport {

	private final RestClient.Builder restClientBuilder;

	@Override
	public void post(String endpoint, String idempotencyKey, String payload) {
		try {
			restClientBuilder.build()
				.post()
				.uri(endpoint)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Idempotency-Key", idempotencyKey)
				.body(payload)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientResponseException exception) {
			int status = exception.getStatusCode().value();
			boolean retryable = status == 408 || status == 429 || status >= 500;
			throw new CallbackDeliveryException(retryable, "HTTP_" + status, "回传目标返回 HTTP " + status);
		}
		catch (ResourceAccessException exception) {
			throw new CallbackDeliveryException(true, "NETWORK_ERROR", "回传目标连接失败");
		}
		catch (RuntimeException exception) {
			throw new CallbackDeliveryException(true, "TRANSPORT_ERROR", "回传请求发送失败");
		}
	}

}
