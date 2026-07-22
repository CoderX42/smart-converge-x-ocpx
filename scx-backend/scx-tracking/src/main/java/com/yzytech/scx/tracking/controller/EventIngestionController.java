package com.yzytech.scx.tracking.controller;

import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import com.yzytech.scx.tracking.dto.EventIngestionResponse;
import com.yzytech.scx.tracking.dto.EventReceiptView;
import com.yzytech.scx.tracking.service.ChannelAuthenticationException;
import com.yzytech.scx.tracking.service.EventConflictException;
import com.yzytech.scx.tracking.service.EventIngestionService;
import com.yzytech.scx.tracking.service.FeedbackStorageException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/open-api/events/v1")
public class EventIngestionController {

	private final EventIngestionService service;

	@PostMapping("/clicks")
	public ResponseEntity<Result<EventIngestionResponse>> click(@RequestHeader("X-Client-Id") String clientId,
			@RequestHeader("X-Key-Version") String keyVersion, @RequestHeader("X-Timestamp") String timestamp,
			@RequestHeader("X-Nonce") String nonce, @RequestHeader("X-Sign") String sign,
			@Valid @RequestBody EventIngestionRequest request) {
		return ingest(clientId, keyVersion, timestamp, nonce, sign, "/open-api/events/v1/clicks", "CLICK", request);
	}

	@PostMapping("/conversions")
	public ResponseEntity<Result<EventIngestionResponse>> conversion(@RequestHeader("X-Client-Id") String clientId,
			@RequestHeader("X-Key-Version") String keyVersion, @RequestHeader("X-Timestamp") String timestamp,
			@RequestHeader("X-Nonce") String nonce, @RequestHeader("X-Sign") String sign,
			@Valid @RequestBody EventIngestionRequest request) {
		return ingest(clientId, keyVersion, timestamp, nonce, sign, "/open-api/events/v1/conversions", null, request);
	}

	@GetMapping("/receipts/{receiptId}")
	public ResponseEntity<Result<EventReceiptView>> receipt(@RequestHeader("X-Client-Id") String clientId,
			@RequestHeader("X-Key-Version") String keyVersion, @RequestHeader("X-Timestamp") String timestamp,
			@RequestHeader("X-Nonce") String nonce, @RequestHeader("X-Sign") String sign,
			@PathVariable String receiptId) {
		try {
			EventReceiptView view = service.receipt(clientId, keyVersion, timestamp, nonce, sign,
					"/open-api/events/v1/receipts/" + receiptId, receiptId);
			return view == null ? response(HttpStatus.NOT_FOUND, Result.fail(404, "回执不存在"))
					: ResponseEntity.ok(Result.success(view));
		}
		catch (ChannelAuthenticationException exception) {
			return response(HttpStatus.valueOf(exception.status()),
					Result.fail(exception.status(), exception.getMessage()));
		}
	}

	private ResponseEntity<Result<EventIngestionResponse>> ingest(String clientId, String keyVersion, String timestamp,
			String nonce, String sign, String path, String forcedEventType, EventIngestionRequest request) {
		try {
			EventIngestionResponse response = service.accept(clientId, keyVersion, timestamp, nonce, sign, path,
					forcedEventType, request);
			HttpStatus status = response.duplicateDelivery() ? HttpStatus.OK : HttpStatus.ACCEPTED;
			return response(status, Result.success(response));
		}
		catch (ChannelAuthenticationException exception) {
			return response(HttpStatus.valueOf(exception.status()),
					Result.fail(exception.status(), exception.getMessage()));
		}
		catch (EventConflictException exception) {
			return response(HttpStatus.CONFLICT, Result.fail(409, exception.getMessage()));
		}
		catch (FeedbackStorageException exception) {
			return response(HttpStatus.SERVICE_UNAVAILABLE, Result.fail(503, exception.getMessage()));
		}
		catch (DataAccessException exception) {
			return response(HttpStatus.SERVICE_UNAVAILABLE, Result.fail(503, "事件持久化服务不可用"));
		}
		catch (IllegalArgumentException exception) {
			return response(HttpStatus.BAD_REQUEST, Result.fail(400, exception.getMessage()));
		}
		catch (IllegalStateException exception) {
			return response(HttpStatus.SERVICE_UNAVAILABLE, Result.fail(503, "事件受理服务不可用"));
		}
	}

	private <T> ResponseEntity<Result<T>> response(HttpStatus status, Result<T> body) {
		return ResponseEntity.status(status).body(body);
	}

}
