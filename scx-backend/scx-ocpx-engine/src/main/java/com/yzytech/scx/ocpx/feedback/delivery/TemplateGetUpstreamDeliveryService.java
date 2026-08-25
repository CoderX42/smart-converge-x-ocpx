package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.audit.service.WireAuditService;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.template.TemplateGetProtocolAdapter;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 模板化 GET 上游投递实现。
 *
 * <p>
 * 仅点击事件会按广告主 URL 模板生成请求并通过 HTTP GET 转发；其他曝光或生命周期事件只进入报表链路。
 */
@Component
public class TemplateGetUpstreamDeliveryService extends UpstreamDeliveryService {

	private final TemplateGetProtocolAdapter protocolAdapter;

	private final OcpxAdvertiserIndex advertiserIndex;

	private final OcpxFlowLogWriter flowLogWriter;

	@Autowired(required = false)
	private WireAuditService wireAuditService;

	public TemplateGetUpstreamDeliveryService(TemplateGetProtocolAdapter protocolAdapter,
			OcpxAdvertiserIndex advertiserIndex, OcpxFlowLogWriter flowLogWriter) {
		this.protocolAdapter = protocolAdapter;
		this.advertiserIndex = advertiserIndex;
		this.flowLogWriter = flowLogWriter;
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		WireAuditService.Handle audit = null;
		try {
			// 当前通用广告主协议只接收点击通知。
			if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
				return;
			}
			OcpxAdvertiser advertiser = advertiserIndex.get(feedback.getAId());
			MacroResolutionService.Resolution resolution = protocolAdapter.buildUpstreamRequest(advertiser, feedback);
			String requestUrl = resolution.url();
			if (!resolution.success()) {
				audit = beginAudit(feedback, null, false);
				completeAudit(audit, null, resolution.rejectionCode());
				flowLogWriter.upstreamDelivery(feedback, false, null, resolution.rejectionCode(), 0L, null, null,
						resolution.audit());
				return;
			}
			audit = beginAudit(feedback, requestUrl, true);
			NetUtil.HttpGetResult result = NetUtil.httpGetResult(requestUrl);
			boolean success = result.statusCode() != null && (result.statusCode() == 200 || result.statusCode() == 204);
			String conclusion = success ? "TEMPLATE_GET_DELIVERED"
					: result.errorCode() == null ? "TEMPLATE_GET_HTTP_FAILED" : "TEMPLATE_GET_" + result.errorCode();
			completeAudit(audit, result, conclusion);
			flowLogWriter.upstreamDelivery(feedback, success, result.statusCode(), conclusion, result.durationMs(),
					result.body(), requestUrl, resolution.audit());
		}
		catch (Exception exception) {
			if (audit == null)
				audit = beginAudit(feedback, null, false);
			if (wireAuditService != null)
				wireAuditService.completeHttp(audit, null, java.util.Map.of(), new byte[0],
						"TEMPLATE_GET_INTERNAL_ERROR", exception, null);
			flowLogWriter.upstreamDelivery(feedback, false, null, "TEMPLATE_GET_INTERNAL_ERROR", 0L);
			errorLog.error("执行上游模板 GET [{}] 异常 type:{}", feedback.getLId(), exception.getClass().getSimpleName());
			throw new RuntimeException(exception);
		}
	}

	private WireAuditService.Handle beginAudit(FeedbackReq feedback, String url, boolean sent) {
		return wireAuditService == null ? null
				: wireAuditService.beginOutbound("ADVERTISER_DELIVERY", "TEMPLATE_GET", "FORMAL", url, sent,
						feedback);
	}

	private void completeAudit(WireAuditService.Handle audit, NetUtil.HttpGetResult result, String conclusion) {
		if (wireAuditService != null)
			wireAuditService.completeOutbound(audit, result, conclusion);
	}

}
