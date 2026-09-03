package com.yzytech.scx.ocpx.callback.web.phoenix;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackHandleResult;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 凤凰新闻 GET Callback 入口；仅合法/重复/扣量结果返回固定成功 JSON。 */
@RestController
@RequestMapping("/phoenix")
@RequiredArgsConstructor
public class PhoenixCallbackController {

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final CallbackService callbackService;

	private final PhoenixCallbackAdapter callbackAdapter;

	@GetMapping(value = "/callback", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PhoenixCallbackResponse> handleCallback(@ModelAttribute PhoenixCallbackRequest request) {
		long startedAt = System.nanoTime();
		try {
			CallbackReq callback = callbackAdapter.toCallback(request);
			CallbackHandleResult result = callbackService.handleCallback(callback);
			callbackLog.info("phoenix callback event:{} requestId:{} duplicate:{} decision:{} durationMs:{}",
					callback.getPartnerActionType(), callback.getFeedback().getReqId(), result.duplicate(),
					result.decision(), elapsedMs(startedAt));
			return ResponseEntity.ok(PhoenixCallbackResponse.success());
		}
		catch (IllegalArgumentException exception) {
			callbackLog.warn("phoenix callback rejected eventTypePresent:{} durationMs:{}", request != null && request.getEvent_type() != null,
					 elapsedMs(startedAt));
			return ResponseEntity.badRequest().build();
		}
		catch (RuntimeException exception) {
			errorLog.error("phoenix callback internal error durationMs:{}", elapsedMs(startedAt));
			return ResponseEntity.internalServerError().build();
		}
	}

	private long elapsedMs(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000L;
	}

}
