package com.yzytech.scx.ocpx.runtime.ingress;

import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/open-api/ocpx/v2/media")
public class FeedbackIngressController {

	private final FeedbackIngressService service;

	public FeedbackIngressController(FeedbackIngressService service) {
		this.service = service;
	}

	@PostMapping("/{mediaCode}/feedback")
	public Mono<ResponseEntity<Object>> feedback(@PathVariable String mediaCode, @RequestBody String rawBody,
			@RequestHeader HttpHeaders headers, ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getRawPath();
		return service.accept(mediaCode, "POST", path, rawBody, headers)
			.map(result -> ResponseEntity.status(HttpStatus.ACCEPTED).body((Object) result))
			.onErrorResume(OcpxException.class, error -> Mono.just(ResponseEntity.status(error.getStatus())
				.body((Object) Map.of("code", error.getStatus().value(), "message", error.getMessage()))));
	}

}
