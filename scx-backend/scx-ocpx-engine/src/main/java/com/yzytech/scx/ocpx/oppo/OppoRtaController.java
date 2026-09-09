package com.yzytech.scx.ocpx.oppo;

import com.fasterxml.jackson.databind.JsonNode;
import com.yzytech.scx.ocpx.rta.relay.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

@RestController
public class OppoRtaController {

	private final OppoRtaService service;

	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(16, 16, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(32), r -> {
				Thread t = new Thread(r, "oppo-rta-response");
				t.setDaemon(true);
				return t;
			}, new ThreadPoolExecutor.AbortPolicy());

	public OppoRtaController(OppoRtaService service) {
		this.service = service;
	}

	@PostMapping(value = "/rta/oppo/{code}", consumes = "application/json")
	public DeferredResult<ResponseEntity<?>> request(@PathVariable String code, HttpServletRequest request)
			throws java.io.IOException {
		long now = System.currentTimeMillis();
		if (request.getQueryString() != null)
			throw new RelayException(400, "OPPO_QUERY_NOT_ALLOWED");
		byte[] bytes = request.getInputStream().readNBytes(65537);
		if (bytes.length > 65536)
			throw new RelayException(413, "OPPO_REQUEST_TOO_LARGE");
		String body;
		try {
			body = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		}
		catch (java.nio.charset.CharacterCodingException e) {
			throw new RelayException(400, "OPPO_ENCODING_INVALID");
		}
		var media = service.media(code);
		var input = service.parse(RelayJson.read(body, JsonNode.class), media, now);
		DeferredResult<ResponseEntity<?>> result = new DeferredResult<>(6000L);
		result.onTimeout(() -> result.setResult(failure(503, "OPPO_TIMEOUT")));
		try {
			executor.execute(() -> {
				try {
					result.setResult(ResponseEntity.ok(service.decide(media, input, now)));
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					result.setResult(failure(503, "OPPO_INTERRUPTED"));
				}
				catch (RelayException e) {
					result.setResult(failure(e.status(), e.getMessage()));
				}
				catch (RuntimeException e) {
					result.setResult(failure(503, "OPPO_UNAVAILABLE"));
				}
			});
		}
		catch (RejectedExecutionException e) {
			result.setResult(failure(503, "OPPO_BUSY"));
		}
		return result;
	}

	private static ResponseEntity<?> failure(int status, String reason) {
		return ResponseEntity.status(status)
			.body(new OppoRtaService.Response(status, reason, new OppoRtaService.Data("", List.of())));
	}

	@ExceptionHandler(RelayException.class)
	public ResponseEntity<?> rejected(RelayException e) {
		return failure(e.status(), e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> unavailable(Exception e) {
		return failure(503, "OPPO_UNAVAILABLE");
	}

	@jakarta.annotation.PreDestroy
	public void close() {
		executor.shutdownNow();
	}

}
