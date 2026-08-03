package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/open-api/ocpx/v1/upstreams")
public class CocoCallbackController {

	private final CallbackIngressService service;

	public CocoCallbackController(CallbackIngressService service) {
		this.service = service;
	}

	@GetMapping("/{upstreamCode}/callback")
	public Mono<ResponseEntity<Map<String, Object>>> callback(@PathVariable String upstreamCode,
			@RequestParam Map<String, String> parameters) {
		return Mono.fromCallable(() -> service.accept(upstreamCode, parameters))
			.subscribeOn(Schedulers.boundedElastic())
			.map(result -> ResponseEntity.ok(success(result)))
			.onErrorResume(OcpxException.class,
					error -> Mono.just(ResponseEntity.status(error.getStatus()).body(failure(error.getMessage()))));
	}

	private Map<String, Object> success(CallbackIngressService.Result result) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", 0);
		body.put("event_id", result.eventId());
		body.put("status", result.status());
		return body;
	}

	private Map<String, Object> failure(String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", 1);
		body.put("message", message);
		return body;
	}

}
