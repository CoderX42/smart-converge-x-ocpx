package com.yzytech.scx.ocpx.feedback.web;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.feedback.service.FeedbackService;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.rta.exception.RtaInfrastructureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

/** 下游媒体 Feedback HTTP 入口。 */
@RestController
public class FeedbackIngressController {

	private static final Set<String> RETIRED_ROUTE_PARAMETERS = Set.of("chid", "mid", "dspid", "linkid");

	private static final String RETIRED_ROUTE_MESSAGE = "这条监测链接已停止使用，请从管理端重新复制最新链接。";

	private static final String INVALID_CLICK_REQUEST_ID_MESSAGE =
			"当前访问的是未完成替换的监测链接模板，请先替换 request_id，或从管理端获取完整测试链接。";

	private static final Pattern UNEXPANDED_MACRO = Pattern.compile("__[A-Z][A-Z0-9_]*__");

	private final Logger errorLog = LoggerFactory.getLogger("error");

	private final Logger feedbackLog = LoggerFactory.getLogger("fb");

	private final FeedbackService feedbackService;

	private final FeedbackRequestAdapter requestAdapter;

	private final OcpxFlowLogWriter flowLogWriter;

	@Autowired
	public FeedbackIngressController(FeedbackService feedbackService, FeedbackRequestAdapter requestAdapter,
			OcpxFlowLogWriter flowLogWriter) {
		this.feedbackService = feedbackService;
		this.requestAdapter = requestAdapter;
		this.flowLogWriter = flowLogWriter;
	}

	public FeedbackIngressController(FeedbackService feedbackService, FeedbackRequestAdapter requestAdapter) {
		this(feedbackService, requestAdapter, null);
	}

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
			return ResponseEntity.badRequest()
				.body(FeedbackIngressResponse.badRequest(RETIRED_ROUTE_MESSAGE));
		}
		if ("CLK".equals(request.getEvent()) && invalidClickRequestId(request.rawRequestId())) {
			feedbackLog.warn("feedback ingress rejected code:CLICK_REQUEST_ID_INVALID mId:{} tId:{} aId:{} lId:{}",
					request.getMId(), request.getTId(), request.getAId(), request.getLId());
			return ResponseEntity.badRequest()
				.body(FeedbackIngressResponse.badRequest(invalidClickRequestMessage(servletRequest)));
		}
		try {
			feedbackLog.info("feedback ingress event:{} mId:{} tId:{} aId:{} lId:{} reqId:{}", request.getEvent(),
					request.getMId(), request.getTId(), request.getAId(), request.getLId(), request.getRequest_id());
			FeedbackReq feedback = requestAdapter.toFeedback(request);
			feedbackService.handleFeedback(feedback);
			if (flowLogWriter != null) {
				flowLogWriter.mediaIngress(feedback, true, "MEDIA_INGRESS_ACCEPTED");
			}
			return ResponseEntity.ok(FeedbackIngressResponse.success());
		}
		catch (Exception exception) {
			if (exception instanceof RtaAdmissionDeniedException) {
				return ResponseEntity.status(403).build();
			}
			if (exception instanceof RtaInfrastructureException) {
				return ResponseEntity.status(503).build();
			}
			errorLog.error("feedback ingress error type:{} event:{} mId:{} tId:{} aId:{} lId:{} reqId:{}",
					exception.getClass().getSimpleName(), request.getEvent(), request.getMId(), request.getTId(),
					request.getAId(), request.getLId(), request.getRequest_id());
			return ResponseEntity.internalServerError().build();
		}
	}

	private boolean invalidClickRequestId(String value) {
		if (!StringUtils.hasText(value) || value.length() > 128 || "__REQUEST_ID__".equals(value)) {
			return true;
		}
		return value.chars().anyMatch(character -> Character.isISOControl(character));
	}

	private String invalidClickRequestMessage(HttpServletRequest servletRequest) {
		Set<String> unresolvedParameters = new LinkedHashSet<>();
		servletRequest.getParameterMap().forEach((name, values) -> {
			if (values != null) {
				for (String value : values) {
					if (value != null && UNEXPANDED_MACRO.matcher(value).find()) {
						unresolvedParameters.add(name);
						break;
					}
				}
			}
		});
		if (unresolvedParameters.isEmpty()) {
			return INVALID_CLICK_REQUEST_ID_MESSAGE;
		}
		return "监测链接中仍有未替换的宏参数：" + String.join("、", unresolvedParameters)
				+ "。请先替换这些参数，或从管理端获取完整测试链接。";
	}

}
