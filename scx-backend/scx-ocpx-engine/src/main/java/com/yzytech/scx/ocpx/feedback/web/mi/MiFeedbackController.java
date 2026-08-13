package com.yzytech.scx.ocpx.feedback.web.mi;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.feedback.service.FeedbackService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;

/**
 * 小米媒体 Feedback HTTP 入口（SGYZ mi 协议迁移）。
 *
 * <p>
 * 成功响应按小米协议固定写出 {@code {"code": 0, "failMsg":"", "success":true}}；异常仅记录日志， 不改写既有响应行为。
 */
@RestController
@RequestMapping(value = "/mi")
@RequiredArgsConstructor
public class MiFeedbackController {

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger feedbackLog = LoggerFactory.getLogger("fb");

	private final FeedbackService feedbackService;

	@GetMapping(value = "feedback")
	public void handleFeedback(MiReq req, HttpServletResponse response) {
		try {
			feedbackLog.info("mi fb req:{}", JSON.toJSONString(req));
			FeedbackReq feedback = MiParamAdapter.getFeedbackReq(req);
			feedbackService.handleFeedback(feedback);

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			// 构建 JSON 字符串
			String jsonOutput = "{\"code\": 0, \"failMsg\":\"\", \"success\":true}";

			// 获取 PrintWriter 来发送响应
			PrintWriter out = response.getWriter();
			out.print(jsonOutput);
			out.flush(); // 确保所有数据都已发送
		}
		catch (Exception exception) {
			errorLog.error("mi handleFeedback error:{},param:{}", exception.getMessage(), JSON.toJSONString(req),
					exception);
		}
	}

}
