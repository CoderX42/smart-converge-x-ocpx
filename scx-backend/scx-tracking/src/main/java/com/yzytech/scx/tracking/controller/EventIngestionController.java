package com.yzytech.scx.tracking.controller;

import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.EventIngestionRequest;
import com.yzytech.scx.tracking.dto.EventIngestionResponse;
import com.yzytech.scx.tracking.service.EventIngestionService;
import com.yzytech.scx.tracking.service.MachineNonceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/open-api/events/v1")
public class EventIngestionController {

	private final TrackingProperties properties;

	private final EventIngestionService service;

	private final MachineNonceService nonceService;

	@PostMapping("/clicks")
	public Result<EventIngestionResponse> click(@RequestHeader("X-Timestamp") String timestamp,
			@RequestHeader("X-Nonce") String nonce, @RequestHeader("X-Sign") String sign,
			@Valid @RequestBody EventIngestionRequest request) {
		request.setEventType("CLICK");
		return ingest(timestamp, nonce, sign, request);
	}

	@PostMapping("/conversions")
	public Result<EventIngestionResponse> conversion(@RequestHeader("X-Timestamp") String timestamp,
			@RequestHeader("X-Nonce") String nonce, @RequestHeader("X-Sign") String sign,
			@Valid @RequestBody EventIngestionRequest request) {
		return ingest(timestamp, nonce, sign, request);
	}

	private Result<EventIngestionResponse> ingest(String timestamp, String nonce, String sign,
			EventIngestionRequest request) {
		try {
			if (!properties.isSandboxEnabled())
				return Result.fail(403, "沙箱事件接入已关闭");
			long ts = Long.parseLong(timestamp);
			long now = System.currentTimeMillis();
			String payloadHash = SignUtils.sha256(service.canonicalPayload(request));
			String expected = SignUtils.hmacSha256(timestamp + nonce + payloadHash, properties.getSandboxSigningKey());
			if (Math.abs(now - ts) > 180_000 || !expected.equalsIgnoreCase(sign) || !nonceService.claim(nonce)) {
				return Result.fail(403, "机器签名无效或 nonce 已使用");
			}
			return Result.success(service.ingest(request));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

}
