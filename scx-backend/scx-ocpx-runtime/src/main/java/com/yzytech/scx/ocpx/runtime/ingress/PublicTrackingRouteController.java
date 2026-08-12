package com.yzytech.scx.ocpx.runtime.ingress;

import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.util.Map;

/** Public tracking entrance. Resolves locally and never fetches the destination. */
@RestController
@RequestMapping("/c")
public class PublicTrackingRouteController {

	private final PublicTrackingRouteService service;

	public PublicTrackingRouteController(PublicTrackingRouteService service) {
		this.service = service;
	}

	@GetMapping("/{taskCode}")
	public ResponseEntity<?> resolve(@PathVariable String taskCode, ServerWebExchange exchange) {
		try {
			PublicTrackingRouteService.Resolution resolution = service.resolve(taskCode,
					exchange.getRequest().getQueryParams());
			return ResponseEntity.status(HttpStatus.FOUND)
				.location(resolution.destination())
				.cacheControl(CacheControl.noStore())
				.build();
		}
		catch (OcpxException error) {
			return ResponseEntity.status(error.getStatus())
				.cacheControl(CacheControl.maxAge(Duration.ZERO).noStore())
				.body(Map.of("code", error.getStatus().value(), "message", error.getMessage()));
		}
	}

}
