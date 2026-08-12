package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/open-api/ocpx/v2/advertisers")
public class UpstreamCallbackController {

	private final UpstreamCallbackDispatcher dispatcher;

	public UpstreamCallbackController(UpstreamCallbackDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@GetMapping("/{advertiserCode}/callback")
	public Mono<ResponseEntity<Object>> callback(@PathVariable String advertiserCode,
			@RequestParam Map<String, String> parameters) {
		return dispatcher.dispatch(advertiserCode, parameters).map(result -> {
			if (!result.isUpstreamFound()) {
				return ResponseEntity.notFound().build();
			}
			UpstreamCallbackResponse response = result.getResponse();
			ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.statusCode());
			response.headers().forEach(builder::header);
			return builder.body(response.body());
		});
	}

}
