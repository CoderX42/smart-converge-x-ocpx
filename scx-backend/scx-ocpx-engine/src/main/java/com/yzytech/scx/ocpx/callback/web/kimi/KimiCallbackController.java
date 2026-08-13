package com.yzytech.scx.ocpx.callback.web.kimi;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KIMI 广告主转化 Callback HTTP 入口（SGYZ kimi 协议迁移）。
 *
 * <p>
 * 原协议为 POST：{@code ext}/{@code s} 走 query，{@code action_type}/{@code action_time} 走 JSON body， 混合绑定原样保留；成功固定返回 {@code {"code":0}}，异常返回 HTTP 500 与 {@code {"code":1}}。
 */
@RestController
@RequestMapping(value = "/kimi")
@RequiredArgsConstructor
public class KimiCallbackController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final CallbackService callbackService;

	private final KimiCallbackAdapter callbackAdapter;

	@PostMapping(value = "callback")
	public String handleCallback(KimiCallbackReq req, @RequestBody KimiCallbackReq req2,
			HttpServletResponse response) {
		try {
			callbackLog.info("kimi cb req1:{}", JSON.toJSONString(req));
			callbackLog.info("kimi cb req2:{}", JSON.toJSONString(req2));
			req.setAction_time(req2.getAction_time());
			req.setAction_type(req2.getAction_type());
			callbackLog.info("kimi cb req:{}", JSON.toJSONString(req));
			CallbackReq callback = callbackAdapter.toCallback(req);
			callbackService.handleCallback(callback);
			return "{\"code\":0}";
		}
		catch (Exception exception) {
			errorLog.error("kimi handleCallback error:{},param:{}", exception.getMessage(), JSON.toJSONString(req),
					exception);
			response.setStatus(500);
			return "{\"code\":1}";
		}
	}

}
