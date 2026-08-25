package com.yzytech.scx.ocpx.callback.web.aerim;

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

/** AERIM GET Callback 入口；所有成功处置均返回空 HTTP 200。 */
@RestController
@RequestMapping("/aerim")
@RequiredArgsConstructor
public class AerimCallbackController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final CallbackService callbackService;

	private final AerimCallbackAdapter callbackAdapter;

	@GetMapping("/callback")
	public ResponseEntity<Void> handleCallback(@ModelAttribute AerimCallbackRequest request) {
		try {
			CallbackReq callback = callbackAdapter.toCallback(request);
			CallbackHandleResult result = callbackService.handleCallback(callback);
			callbackLog.info("aerim callback action:{} duplicate:{} decision:{}", request.getAction_type(),
					result.duplicate(), result.decision());
			return ResponseEntity.ok().build();
		}
		catch (IllegalArgumentException exception) {
			callbackLog.warn("aerim callback rejected action:{} reason:{}", request.getAction_type(),
					exception.getMessage());
			return ResponseEntity.badRequest().build();
		}
		catch (RuntimeException exception) {
			errorLog.error("aerim callback internal error action:{}", request.getAction_type(), exception);
			return ResponseEntity.internalServerError().build();
		}
	}

}
