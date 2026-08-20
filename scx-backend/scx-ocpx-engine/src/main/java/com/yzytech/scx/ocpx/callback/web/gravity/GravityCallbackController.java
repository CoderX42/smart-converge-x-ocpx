package com.yzytech.scx.ocpx.callback.web.gravity;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackHandleResult;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 引力归因成功 Callback 入口。 */
@RestController
@RequestMapping("/gravity")
@RequiredArgsConstructor
public class GravityCallbackController {

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final CallbackService callbackService;

	private final GravityCallbackAdapter callbackAdapter;

	@GetMapping(value = "/callback", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<GravityCallbackResponse> handleCallback(
			@RequestParam(value = "ctx", required = false) String token) {
		long startedAt = System.nanoTime();
		try {
			CallbackReq callback = callbackAdapter.toCallback(token);
			CallbackHandleResult result = callbackService.handleCallback(callback);
			callbackLog.info(
					"gravity callback requestId:{} mediaId:{} taskId:{} advertiserId:{} linkId:{} httpStatus:200 durationMs:{} conclusion:{}",
					callback.getFeedback().getReqId(), callback.getFeedback().getMId(), callback.getFeedback().getTId(),
					callback.getFeedback().getAId(), callback.getFeedback().getLId(), elapsedMs(startedAt),
					result.duplicate() ? "DUPLICATE_SUCCESS" : result.decision());
			return ResponseEntity.ok(GravityCallbackResponse.success());
		}
		catch (IllegalArgumentException exception) {
			callbackLog.warn("gravity callback httpStatus:400 durationMs:{} conclusion:REJECTED", elapsedMs(startedAt));
			return ResponseEntity.badRequest().body(GravityCallbackResponse.failed());
		}
		catch (RuntimeException exception) {
			errorLog.error("gravity callback httpStatus:400 durationMs:{} conclusion:PROCESSING_FAILED",
					elapsedMs(startedAt));
			return ResponseEntity.badRequest().body(GravityCallbackResponse.failed());
		}
	}

	private long elapsedMs(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000L;
	}

}
