package com.yzytech.scx.ocpx.callback.delivery;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.audit.service.WireAuditService;
import com.yzytech.scx.ocpx.callback.service.TestCallbackCollectorService;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** 使用 Feedback 中保存的通用回调地址向下游媒体回传转化事件。 */
@Service
@RequiredArgsConstructor
public class CallbackUrlDownstreamDeliveryService extends DownstreamDeliveryService {

	private final PublicCallbackUrlPolicy callbackUrlPolicy;

	private final TestCallbackCollectorService testCallbackCollectorService;

	private final OcpxFlowLogWriter flowLogWriter;

	@Autowired(required = false)
	private WireAuditService wireAuditService;

	/**
	 * 解码原媒体回调地址，追加 {@code event_type} 后发起 GET 请求。
	 * @param request 已通过 Callback 去重与扣量判定的请求
	 */
	@Override
	public void deliver(CallbackReq request) {
		WireAuditService.Handle audit = null;
		Integer eventType = request.getActionType().getIndex();
		if (eventType == null) {
			audit = beginAudit(request, null, false);
			completeAudit(audit, null, "MEDIA_DELIVERY_EVENT_MISSING");
			log.info("downstream callback event is null");
			flowLogWriter.downstreamDelivery(request, false, null, "MEDIA_DELIVERY_EVENT_MISSING", 0L, null, null);
			return;
		}
		if (request.getFeedback().getCallbackUrl() == null) {
			audit = beginAudit(request, null, false);
			completeAudit(audit, null, "MEDIA_DELIVERY_CALLBACK_MISSING");
			log.info("downstream callback URL is null");
			flowLogWriter.downstreamDelivery(request, false, null, "MEDIA_DELIVERY_CALLBACK_MISSING", 0L, null, null);
			return;
		}
		try {
			// Feedback 入站时已严格 URL 编码，此处恢复媒体提供的原始地址。
			String callbackUrl = URLDecoder.decode(request.getFeedback().getCallbackUrl(), StandardCharsets.UTF_8);
			callbackUrl = callbackUrlPolicy.requirePublicHttpUrl(callbackUrl);
			callbackUrl = callbackUrlPolicy.appendCallbackFields(callbackUrl, eventType, request.getCurrencyAmount());
			callbackUrl = testCallbackCollectorService.signDeliveryIfTestCallback(callbackUrl, eventType,
					request.getCurrencyAmount());
			audit = beginAudit(request, callbackUrl, true);
			NetUtil.HttpGetResult result = NetUtil.httpGetResult(callbackUrl);
			boolean success = result.statusCode() != null && (result.statusCode() == 200 || result.statusCode() == 204);
			String conclusion = success ? "MEDIA_DELIVERED"
					: result.errorCode() == null ? "MEDIA_HTTP_FAILED" : "MEDIA_" + result.errorCode();
			completeAudit(audit, result, conclusion);
			flowLogWriter.downstreamDelivery(request, success, result.statusCode(), conclusion, result.durationMs(),
					result.body(), callbackUrl);
		}
		catch (IllegalArgumentException exception) {
			if (audit == null)
				audit = beginAudit(request, null, false);
			if (wireAuditService != null)
				wireAuditService.completeHttp(audit, null, java.util.Map.of(), new byte[0], "MEDIA_URL_REJECTED",
						exception, null);
			flowLogWriter.downstreamDelivery(request, false, null, "MEDIA_URL_REJECTED", 0L, null, null);
			throw exception;
		}
		catch (RuntimeException exception) {
			if (audit == null)
				audit = beginAudit(request, null, false);
			if (wireAuditService != null)
				wireAuditService.completeHttp(audit, null, java.util.Map.of(), new byte[0], "MEDIA_INTERNAL_ERROR",
						exception, null);
			flowLogWriter.downstreamDelivery(request, false, null, "MEDIA_INTERNAL_ERROR", 0L, null, null);
			throw exception;
		}
	}

	private WireAuditService.Handle beginAudit(CallbackReq request, String url, boolean sent) {
		return wireAuditService == null ? null
				: wireAuditService.beginOutbound("MEDIA_DELIVERY", "MEDIA_CALLBACK", traffic(request), url, sent,
						request.getFeedback());
	}

	private void completeAudit(WireAuditService.Handle audit, NetUtil.HttpGetResult result, String conclusion) {
		if (wireAuditService != null)
			wireAuditService.completeOutbound(audit, result, conclusion);
	}

	private String traffic(CallbackReq request) {
		String requestId = request.getFeedback().getReqId();
		return requestId != null && requestId.startsWith("SCX-UAT-") ? "TEST" : "FORMAL";
	}

}
