package com.yzytech.scx.ocpx.feedback.web;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.feedback.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 下游媒体 Feedback HTTP 入口。 */
@RestController
@RequiredArgsConstructor
public class FeedbackIngressController {

	private static final Set<String> RETIRED_ROUTE_PARAMETERS = Set.of("chid", "mid", "dspid", "linkid");

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger feedbackLog = LoggerFactory.getLogger("fb");

	private final FeedbackService feedbackService;

	private final FeedbackRequestAdapter requestAdapter;

	/**
	 * 接收媒体曝光或点击，将查询参数标准化后进入 Feedback 处理链路。
	 *
	 * <p>
	 * 成功响应沿用旧协议固定 JSON；事实事务失败时返回服务端错误，避免媒体误认为事件已经持久化。
	 * @param request 通用媒体查询参数
	 * @return 兼容旧协议的 HTTP 处理结果
	 */
	@GetMapping("/feedback")
	public ResponseEntity<FeedbackIngressResponse> handleFeedback(@Valid @ModelAttribute FeedbackIngressRequest request,
			HttpServletRequest servletRequest) {
		if (RETIRED_ROUTE_PARAMETERS.stream().anyMatch(servletRequest.getParameterMap()::containsKey)) {
			return ResponseEntity.badRequest().build();
		}
		try {
			feedbackLog.info("feedback ingress event:{} mId:{} tId:{} aId:{} lId:{} reqId:{}", request.getEvent(),
					request.getMId(), request.getTId(), request.getAId(), request.getLId(), request.getRequest_id());
			FeedbackReq feedback = requestAdapter.toFeedback(request);
			feedbackService.handleFeedback(feedback);
			return ResponseEntity.ok(FeedbackIngressResponse.success());
		}
		catch (Exception exception) {
			errorLog.error("feedback ingress error type:{} event:{} mId:{} tId:{} aId:{} lId:{} reqId:{}",
					exception.getClass().getSimpleName(), request.getEvent(), request.getMId(), request.getTId(),
					request.getAId(), request.getLId(), request.getRequest_id());
			return ResponseEntity.internalServerError().build();
		}
	}

}
