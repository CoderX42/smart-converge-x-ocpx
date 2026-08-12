package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.template.TemplateGetProtocolAdapter;
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

	public TemplateGetUpstreamDeliveryService(TemplateGetProtocolAdapter protocolAdapter,
			OcpxAdvertiserIndex advertiserIndex) {
		this.protocolAdapter = protocolAdapter;
		this.advertiserIndex = advertiserIndex;
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
				return;
			}
			log.info("upstream template GET linkId:{} req:{}", feedback.getLId(), requestUrl);
			String content = NetUtil.httpGet(requestUrl, null);
			log.info("upstream template GET linkId:{}, content:{}", feedback.getLId(), content);
		}
		catch (Exception exception) {
			errorLog.error("执行上游模板 GET [{}] 异常 message:{}", feedback.getLId(), exception.getMessage(), exception);
			throw new RuntimeException(exception);
		}
	}

}
