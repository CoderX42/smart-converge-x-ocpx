package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.template.TemplateGetProtocolAdapter;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.springframework.stereotype.Component;

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

	public TemplateGetUpstreamDeliveryService(TemplateGetProtocolAdapter protocolAdapter,
			OcpxAdvertiserIndex advertiserIndex, OcpxFlowLogWriter flowLogWriter) {
		this.protocolAdapter = protocolAdapter;
		this.advertiserIndex = advertiserIndex;
		this.flowLogWriter = flowLogWriter;
	}

	@Override
	public void deliver(FeedbackReq feedback) {
		try {
			// 当前通用广告主协议只接收点击通知。
			if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
				return;
			}
			OcpxAdvertiser advertiser = advertiserIndex.get(feedback.getAId());
			String requestUrl = protocolAdapter.buildUpstreamRequestUrl(advertiser, feedback);
			if (requestUrl == null) {
				flowLogWriter.upstreamDelivery(feedback, false, null, "TEMPLATE_GET_REQUEST_REJECTED", 0L);
				return;
			}
			NetUtil.HttpGetResult result = NetUtil.httpGetResult(requestUrl);
			boolean success = result.statusCode() != null && (result.statusCode() == 200 || result.statusCode() == 204);
			String conclusion = success ? "TEMPLATE_GET_DELIVERED"
					: result.errorCode() == null ? "TEMPLATE_GET_HTTP_FAILED" : "TEMPLATE_GET_" + result.errorCode();
			flowLogWriter.upstreamDelivery(feedback, success, result.statusCode(), conclusion, result.durationMs(),
					result.body());
		}
		catch (Exception exception) {
			flowLogWriter.upstreamDelivery(feedback, false, null, "TEMPLATE_GET_INTERNAL_ERROR", 0L);
			errorLog.error("执行上游模板 GET [{}] 异常 type:{}", feedback.getLId(), exception.getClass().getSimpleName());
			throw new RuntimeException(exception);
		}
	}

}
