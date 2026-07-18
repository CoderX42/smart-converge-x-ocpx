package com.yzytech.scx.tracking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class HttpRtaTransport implements RtaTransport {

	private final RestClient.Builder restClientBuilder;

	@Override
	public String decide(String endpoint, String payload) {
		try {
			String response = restClientBuilder.build()
				.post()
				.uri(endpoint)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.body(String.class);
			return response == null ? "" : response;
		}
		catch (RestClientResponseException exception) {
			throw new RtaTransportException("HTTP_" + exception.getStatusCode().value(), "RTA 目标返回异常状态");
		}
		catch (ResourceAccessException exception) {
			throw new RtaTransportException("NETWORK_ERROR", "RTA 目标连接失败");
		}
		catch (RuntimeException exception) {
			throw new RtaTransportException("TRANSPORT_ERROR", "RTA 请求发送失败");
		}
	}

}
