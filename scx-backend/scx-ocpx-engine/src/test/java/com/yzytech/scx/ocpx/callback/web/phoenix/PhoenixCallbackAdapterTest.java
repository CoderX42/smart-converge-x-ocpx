package com.yzytech.scx.ocpx.callback.web.phoenix;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMediaIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhoenixCallbackAdapterTest {

	private final OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("v1", Map.of("v1", "phoenix-callback-key"), List.of("v1"));

	private final OcpxAdvertiserIndex advertiserIndex = mock(OcpxAdvertiserIndex.class);

	private final OcpxPromotionLinkIndex linkIndex = mock(OcpxPromotionLinkIndex.class);

	private final OcpxMonitorTaskIndex taskIndex = mock(OcpxMonitorTaskIndex.class);

	private final OcpxMediaIndex mediaIndex = mock(OcpxMediaIndex.class);

	private PhoenixCallbackAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new PhoenixCallbackAdapter(crypto, advertiserIndex, linkIndex, taskIndex, mediaIndex);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("advertiser-id");
		advertiser.setUpstreamProtocol(OcpxUpstreamProtocol.PHOENIX);
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-id");
		link.setAdvertiserId("advertiser-id");
		link.setPlatform(1);
		link.setLinkUrl("https://api.feather.ifeng.com/d/cli?cid=123&gid=456&aid=789&mid=999&channel=abc"
				+ "&reqid=__REQID__&os=__OS__&ts=__TS__&callback=__CALLBACK__"
				+ "&oaid=__OAID__&oaid_md5=__OAIDMD5__");
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-id");
		task.setAdvertiserId("advertiser-id");
		task.setPromotionLinkId("link-id");
		task.setMediaId("media-id");
		task.setConversionEvent("ACTIVATE");
		when(advertiserIndex.get("advertiser-id")).thenReturn(advertiser);
		when(linkIndex.get("link-id")).thenReturn(link);
		when(taskIndex.get("task-id")).thenReturn(task);
		when(mediaIndex.get("media-id")).thenReturn(new OcpxMedia());
	}

	@ParameterizedTest
	@CsvSource({ "active,ACTIVATE", "download,DOWNLOAD", "activity,ACTIVITY", "addiction,ADDICTION", "pay_amount,PAYMENT" })
	void mapsPdfEventTypes(String event, ActionTypeEnum expected) {
		String ext = crypto.encryptCallback(JSON.toJSONString(feedback()));
		PhoenixCallbackRequest request = new PhoenixCallbackRequest();
		request.setKid("v1");
		request.setExt(ext);
		request.setEvent_type(event);

		assertThat(adapter.toCallback(request).getActionType()).isEqualTo(expected);
	}

	@Test
	void missingEventDefaultsToActive() {
		String ext = crypto.encryptCallback(JSON.toJSONString(feedback()));
		PhoenixCallbackRequest request = new PhoenixCallbackRequest();
		request.setKid("v1");
		request.setExt(ext);

		assertThat(adapter.toCallback(request).getActionType()).isEqualTo(ActionTypeEnum.ACTIVATE);
	}

	private FeedbackReq feedback() {
		return FeedbackReq.builder().mId("media-id").tId("task-id").aId("advertiser-id").lId("link-id")
				.reqId("request-id").event(EventTypeEnum.CLICK).build();
	}

}
