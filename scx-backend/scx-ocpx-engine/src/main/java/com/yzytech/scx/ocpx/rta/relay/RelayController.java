package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/rta")
public class RelayController {

	private final RelayService service;

	private final RelayRepository repository;

	private final RelaySecurity security;

	private final RtaAdapterRegistry adapters;

	public RelayController(RelayService service, RelayRepository repository, RelaySecurity security,
			RtaAdapterRegistry adapters) {
		this.adapters = adapters;
		this.service = service;
		this.repository = repository;
		this.security = security;
	}

	@PostMapping(value = "/requests", consumes = "application/json")
	public ResponseEntity<?> accept(HttpServletRequest request, @RequestHeader Map<String, String> headers)
			throws java.io.IOException {
		var normalized = new java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		normalized.putAll(headers);
		headers = normalized;
		String body = body(request);
		Media media = repository.mediaByCredential(headers.get("x-rta-key"));
		security.verify(media.credentialRef(), request.getRequestURI(), body, headers, System.currentTimeMillis());
		return ResponseEntity.accepted().body(service.accept(RelayJson.read(body, Request.class), media));
	}

	@PostMapping(value = "/callbacks/{accessCode}", consumes = "application/json")
	public ResponseEntity<?> callback(@PathVariable String accessCode, HttpServletRequest request,
			@RequestHeader Map<String, String> headers) throws java.io.IOException {
		var normalized = new java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		normalized.putAll(headers);
		headers = normalized;
		String body = body(request);
		Upstream upstream = repository.upstream("access_code", accessCode);
		var decision = adapters.require(upstream.protocol())
			.callback(upstream, request.getRequestURI(), body, headers, security);
		var result = service.decide(decision, accessCode);
		return ResponseEntity.ok(Map.of("relayRequestId", result.getRelayRequestId(), "status", result.getStatus()));
	}

	private String body(HttpServletRequest request) throws java.io.IOException {
		if (request.getQueryString() != null)
			throw new RelayException(400, "RTA_QUERY_NOT_ALLOWED");
		byte[] bytes = request.getInputStream().readNBytes(65537);
		if (bytes.length > 65536)
			throw new RelayException(413, "RTA_REQUEST_TOO_LARGE");
		try {
			return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		}
		catch (java.nio.charset.CharacterCodingException e) {
			throw new RelayException(400, "RTA_ENCODING_INVALID");
		}
	}

	@ExceptionHandler(RelayException.class)
	public ResponseEntity<?> rejected(RelayException e) {
		return ResponseEntity.status(e.status()).body(Map.of("code", e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> unavailable(Exception e) {
		return ResponseEntity.status(503).body(Map.of("code", "RTA_UNAVAILABLE"));
	}

}
