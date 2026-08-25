package com.yzytech.scx.ocpx.callback.web;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import com.yzytech.scx.ocpx.protocol.CallbackRequestAdapter;
import lombok.RequiredArgsConstructor;
import com.yzytech.scx.ocpx.audit.web.WireAuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/** 上游广告主转化 Callback HTTP 入口。 */
@RestController
@RequiredArgsConstructor
public class UpstreamCallbackController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final CallbackService callbackService;

	private final CallbackRequestAdapter requestAdapter;

	/**
	 * 解密并处理广告主转化回调；成功保持空响应体，协议解析失败返回 HTTP 500。
	 * @param request 通用 Callback 查询参数
	 * @return 无响应体的 HTTP 处理结果
	 */
	@GetMapping("/callback")
	public ResponseEntity<Void> handleCallback(@ModelAttribute UpstreamCallbackRequest request,
			HttpServletRequest servletRequest) {
		try {
			callbackLog.info("upstream callback received eventType:{} keyVersionPresent:{}", request.getEvent_type(),
					request.getKid() != null);
			CallbackReq callback = requestAdapter.toCallback(request);
			WireAuditRequestContext.enrich(servletRequest, callback);
			callbackService.handleCallback(callback);
			return ResponseEntity.ok().build();
		}
		catch (Exception exception) {
			errorLog.error("upstream callback error type:{} eventType:{}", exception.getClass().getSimpleName(),
					request.getEvent_type(), exception);
			return ResponseEntity.internalServerError().build();
		}
	}

}
