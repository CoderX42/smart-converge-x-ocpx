package com.yzytech.scx.ocpx.runtime.delivery;

import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.runtime.security.OutboundUrlValidator;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeliveryHttpClient {

	private final WebClient webClient;

	private final OutboundUrlValidator urlValidator;

	public DeliveryHttpClient(WebClient ocpxWebClient, OutboundUrlValidator urlValidator) {
		this.webClient = ocpxWebClient;
		this.urlValidator = urlValidator;
	}

	public Mono<AdapterHttpResponse> exchange(AdapterHttpRequest request) {
		return Mono.defer(() -> {
			urlValidator.validate(request.uri());
			WebClient.RequestBodySpec spec = webClient.method(HttpMethod.valueOf(request.method()))
				.uri(request.uri())
				.headers(headers -> request.headers().forEach(headers::set));
			WebClient.RequestHeadersSpec<?> exchange = request.body() == null ? spec : spec.bodyValue(request.body());
			return exchange.exchangeToMono(response -> response.bodyToMono(String.class)
				.defaultIfEmpty("")
				.map(body -> new AdapterHttpResponse(response.statusCode().value(), response.headers().asHttpHeaders(),
						body.length() > 4096 ? body.substring(0, 4096) : body)));
		}).timeout(Duration.ofSeconds(3));
	}

}
