package com.yzytech.scx.ocpx.feedback.delivery;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.template.TemplateGetProtocolAdapter;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateGetUpstreamDeliveryServiceTest {

	@Test
	void recordsOnlySanitizedHttpOutcome() {
		TemplateGetProtocolAdapter protocolAdapter = mock(TemplateGetProtocolAdapter.class);
		OcpxAdvertiserIndex advertiserIndex = mock(OcpxAdvertiserIndex.class);
		OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		FeedbackReq feedback = FeedbackReq.builder()
			.aId("advertiser-1")
			.lId("link-1")
			.event(EventTypeEnum.CLICK)
			.reqId("SCX-UAT-20260817183000-abcdef123456")
			.build();
		when(advertiserIndex.get("advertiser-1")).thenReturn(advertiser);
		when(protocolAdapter.buildUpstreamRequestUrl(advertiser, feedback))
			.thenReturn("https://partner.example/click?idfa=DEVICE_SECRET");
		TemplateGetUpstreamDeliveryService service = new TemplateGetUpstreamDeliveryService(protocolAdapter,
				advertiserIndex, flowLogWriter);

		try (MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class)) {
			netUtil.when(() -> NetUtil.httpGetResult(anyString()))
				.thenReturn(new NetUtil.HttpGetResult(200, "partner-body", null, 12L));

			service.deliver(feedback);
		}

		verify(flowLogWriter).upstreamDelivery(feedback, true, 200, "TEMPLATE_GET_DELIVERED", 12L);
	}

}
