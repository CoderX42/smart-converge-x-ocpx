package com.yzytech.scx.ocpx.callback.web.renrenvideo;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackHandleResult;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 人人视频 GET Callback 入口；成功、重复和扣量均返回空 HTTP 200。 */
@RestController
@RequestMapping("/renren-video")
@RequiredArgsConstructor
public class RenrenVideoCallbackController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final CallbackService callbackService;

	private final RenrenVideoCallbackAdapter callbackAdapter;

	@GetMapping("/callback")
	public ResponseEntity<Void> handleCallback(@ModelAttribute RenrenVideoCallbackRequest request) {
		try {
			CallbackReq callback = callbackAdapter.toCallback(request);
			CallbackHandleResult result = callbackService.handleCallback(callback);
			callbackLog.info("renren video callback event:{} duplicate:{} decision:{}", request.getEvent(),
					result.duplicate(), result.decision());
			return ResponseEntity.ok().build();
		}
		catch (IllegalArgumentException exception) {
			callbackLog.warn("renren video callback rejected event:{} reason:{}", request.getEvent(),
					exception.getMessage());
			return ResponseEntity.badRequest().build();
		}
		catch (RuntimeException exception) {
			errorLog.error("renren video callback internal error event:{}", request.getEvent(), exception);
			return ResponseEntity.internalServerError().build();
		}
	}

}
