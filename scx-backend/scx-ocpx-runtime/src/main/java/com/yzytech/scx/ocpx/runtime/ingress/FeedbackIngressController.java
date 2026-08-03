package com.yzytech.scx.ocpx.runtime.ingress;

import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
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
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/open-api/ocpx/v1/channels")
public class FeedbackIngressController {

	private final FeedbackIngressService service;

	public FeedbackIngressController(FeedbackIngressService service) {
		this.service = service;
	}

	@PostMapping("/{channelCode}/feedback")
	public Mono<ResponseEntity<Object>> feedback(@PathVariable String channelCode, @RequestBody String rawBody,
			@RequestHeader org.springframework.http.HttpHeaders headers, ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getRawPath();
		return Mono.fromCallable(() -> service.accept(channelCode, "POST", path, rawBody, headers))
			.subscribeOn(Schedulers.boundedElastic())
			.map(result -> {
				if (result.status() == IngressResult.Status.CONFLICT) {
					return ResponseEntity.status(HttpStatus.CONFLICT).body((Object) result);
				}
				return ResponseEntity
					.status(result.status() == IngressResult.Status.ACCEPTED ? HttpStatus.ACCEPTED : HttpStatus.OK)
					.body((Object) result);
			})
			.onErrorResume(OcpxException.class, error -> Mono.just(ResponseEntity.status(error.getStatus())
				.body((Object) Map.of("code", error.getStatus().value(), "message", error.getMessage()))));
	}

}
