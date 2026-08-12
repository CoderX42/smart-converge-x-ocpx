package com.yzytech.scx.ocpx.controller.general;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.controller.general.dto.GeneralReq;
import com.yzytech.scx.ocpx.service.wrapper.FeedbackService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;

@RestController
@RequestMapping("/general")
@RequiredArgsConstructor
public class GeneralController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger feedbackLog = LoggerFactory.getLogger("fb");

	private final FeedbackService feedbackService;

	@GetMapping("/feedback")
	public void handleFeedback(GeneralReq request, HttpServletResponse response) {
		try {
			feedbackLog.info("general fb req:{}", JSON.toJSONString(request));
			FeedbackReq feedback = GeneralParamAdapter.getFeedbackReq(request);
			feedbackService.handleFeedback(feedback);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			PrintWriter writer = response.getWriter();
			writer.print("{\"ret\":0,\"code\":200,\"msg\":\"提交成功\",\"data\":{}}");
			writer.flush();
		}
		catch (Exception exception) {
			errorLog.error("general handleFeedback error:{},param:{}", exception.getMessage(),
					JSON.toJSONString(request), exception);
		}
	}

}
