package com.yzytech.scx.tracking.controller;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.tracking.dto.RtaDecisionRequest;
import com.yzytech.scx.tracking.dto.RtaDecisionResponse;
import com.yzytech.scx.tracking.service.ChannelAuthenticationException;
import com.yzytech.scx.tracking.service.ChannelRequestAuthenticator;
import com.yzytech.scx.tracking.service.FeedbackStorageException;
import com.yzytech.scx.tracking.service.RtaDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/open-api/rta/v1")
public class RtaDecisionController {

	private final RtaDecisionService service;

	private final ChannelRequestAuthenticator authenticator;

	@PostMapping("/decisions")
	public ResponseEntity<Result<RtaDecisionResponse>> decision(@RequestHeader("X-Client-Id") String clientId,
			@RequestHeader("X-Key-Version") String keyVersion, @RequestHeader("X-Timestamp") String timestamp,
			@RequestHeader("X-Nonce") String nonce, @RequestHeader("X-Sign") String sign,
			@Valid @RequestBody RtaDecisionRequest request) {
		try {
			String canonical = service.canonicalPayload(request);
			authenticator.authenticate(clientId, keyVersion, timestamp, nonce, sign, "POST",
					"/open-api/rta/v1/decisions", canonical);
			return ResponseEntity.ok(Result.success(service.decide(request, clientId)));
		}
		catch (ChannelAuthenticationException exception) {
			return ResponseEntity.status(exception.status())
				.body(Result.fail(exception.status(), exception.getMessage()));
		}
		catch (IllegalArgumentException exception) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, exception.getMessage()));
		}
		catch (FeedbackStorageException exception) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.fail(503, "RTA 可靠存储不可用"));
		}
	}

}
