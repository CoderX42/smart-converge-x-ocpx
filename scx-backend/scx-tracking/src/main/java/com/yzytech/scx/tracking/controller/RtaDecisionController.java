package com.yzytech.scx.tracking.controller;

import com.yzytech.scx.common.core.sign.SignUtils;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.tracking.config.TrackingProperties;
import com.yzytech.scx.tracking.dto.RtaDecisionRequest;
import com.yzytech.scx.tracking.dto.RtaDecisionResponse;
import com.yzytech.scx.tracking.service.MachineNonceService;
import com.yzytech.scx.tracking.service.RtaDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/open-api/rta/v1")
public class RtaDecisionController {

	private final TrackingProperties properties;

	private final RtaDecisionService service;

	private final MachineNonceService nonceService;

	@PostMapping("/decisions")
	public Result<RtaDecisionResponse> decision(@RequestHeader("X-Timestamp") String timestamp,
			@RequestHeader("X-Nonce") String nonce, @RequestHeader("X-Sign") String sign,
			@Valid @RequestBody RtaDecisionRequest request) {
		try {
			if (!properties.isSandboxEnabled())
				return Result.fail(403, "沙箱 RTA 接入已关闭");
			long value = Long.parseLong(timestamp);
			String expected = SignUtils.hmacSha256(
					timestamp + nonce + SignUtils.sha256(service.canonicalPayload(request)),
					properties.getSandboxSigningKey());
			if (Math.abs(System.currentTimeMillis() - value) > 180_000 || !expected.equalsIgnoreCase(sign)
					|| !nonceService.claim(nonce)) {
				return Result.fail(403, "机器签名无效或 nonce 已使用");
			}
			return Result.success(service.decide(request));
		}
		catch (IllegalArgumentException exception) {
			return Result.fail(exception.getMessage());
		}
	}

}
