package com.yzytech.scx.ocpx.runtime.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/api-debug")
public class InternalApiDebugController {

	private final InternalApiDebugService service;

	private final ObjectMapper objectMapper;

	public InternalApiDebugController(InternalApiDebugService service, ObjectMapper objectMapper) {
		this.service = service;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/executions")
	public Mono<ResponseEntity<InternalApiDebugService.Accepted>> execute(@RequestBody String rawBody,
			@RequestHeader HttpHeaders headers) {
		try {
			service.authenticate("POST", "/internal/api-debug/executions", rawBody, headers);
			InternalApiDebugService.ExecuteRequest request = objectMapper.readValue(rawBody,
					InternalApiDebugService.ExecuteRequest.class);
			return service.execute(request).map(result -> ResponseEntity.accepted().body(result));
		}
		catch (Exception error) {
			return Mono.just(ResponseEntity.status(403).build());
		}
	}

	@GetMapping("/logs")
	public ResponseEntity<InternalApiDebugService.RecentLogs> logs(@RequestHeader HttpHeaders headers, String level,
			String logger, String keyword, @RequestParam(defaultValue = "0") long cursor) {
		try {
			service.authenticate("GET", "/internal/api-debug/logs", "", headers);
			return ResponseEntity.ok(service.recentLogs(level, logger, keyword, cursor));
		}
		catch (Exception error) {
			return ResponseEntity.status(403).build();
		}
	}

}
