package com.yzytech.scx.ocpx.manager.union;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.manager.union.dto.UnionCallbackReq;
import com.yzytech.scx.ocpx.service.wrapper.CallbackService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/union", "//union" })
@RequiredArgsConstructor
public class UnionController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger callbackLog = LoggerFactory.getLogger("cb");

	private final CallbackService callbackService;

	private final UnionParamAdapter unionParamAdapter;

	@GetMapping("/callback")
	public void handleCallback(UnionCallbackReq request, HttpServletResponse response) {
		try {
			callbackLog.info("union cb req:{}", JSON.toJSONString(request));
			CallbackReq callback = unionParamAdapter.getCallbackReq(request);
			callbackService.handleFeedback(callback);
		}
		catch (Exception exception) {
			errorLog.error("union handleCallback error:{},param:{}", exception.getMessage(), JSON.toJSONString(request),
					exception);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

}
