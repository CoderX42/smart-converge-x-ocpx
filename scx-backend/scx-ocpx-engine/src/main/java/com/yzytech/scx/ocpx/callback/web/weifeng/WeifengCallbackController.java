package com.yzytech.scx.ocpx.callback.web.weifeng;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.audit.web.WireAuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
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

/** 微风互动 GET Callback 入口；成功、重复和扣量处置均返回空 HTTP 200。 */
@RestController
@RequestMapping("/weifeng")
@RequiredArgsConstructor
public class WeifengCallbackController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final CallbackService callbackService;

	private final WeifengCallbackAdapter callbackAdapter;

	@GetMapping("/callback")
	public ResponseEntity<Void> handleCallback(@ModelAttribute WeifengCallbackRequest request,
			HttpServletRequest servletRequest) {
		try {
			CallbackReq callback = callbackAdapter.toCallback(request);
			WireAuditRequestContext.enrich(servletRequest, callback);
			CallbackHandleResult result = callbackService.handleCallback(callback);
			callbackLog.info("weifeng callback event:{} duplicate:{} decision:{}", request.getEvent(),
					result.duplicate(), result.decision());
			return ResponseEntity.ok().build();
		}
		catch (IllegalArgumentException exception) {
			callbackLog.warn("weifeng callback rejected event:{} reason:{}", request.getEvent(),
					exception.getMessage());
			return ResponseEntity.badRequest().build();
		}
		catch (RuntimeException exception) {
			errorLog.error("weifeng callback internal error event:{}", request.getEvent(), exception);
			return ResponseEntity.internalServerError().build();
		}
	}

}
