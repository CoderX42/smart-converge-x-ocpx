package com.yzytech.scx.ocpx.protocol.template;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialProperties;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialResolver;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.callback.web.UpstreamCallbackRequest;
import com.yzytech.scx.ocpx.shared.util.AesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateGetProtocolAdapterTest {

	private final OcpxMonitorTaskIndex monitorTaskIndex = new OcpxMonitorTaskIndex();

	@AfterEach
	void clearIndex() {
		monitorTaskIndex.clear();
	}

	@Test
	void roundTripsTheAesCallbackAndKeepsTheScxIds() {
		TemplateGetProtocolAdapter adapter = adapter();
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-1")
			.build();

		String callbackUrl = adapter.buildCallbackUrl(feedback);
		String ext = callbackUrl.substring(callbackUrl.indexOf("ext=") + 4, callbackUrl.indexOf("&s="));
		String clearText = AesUtil.decryptBase64(ext, "local-dev-key-01");

		assertThat(callbackUrl).startsWith("http://127.0.0.1:10000/ocpx/callback?ext=");
		assertThat(callbackUrl).endsWith("&s=link-id_task-id");
		assertThat(clearText)
			.contains("\"mId\":\"media-id\"", "\"tId\":\"task-id\"", "\"aId\":\"advertiser-id\"",
					"\"lId\":\"link-id\"")
			.doesNotContain("\"MId\"", "\"TId\"", "\"AId\"", "\"LId\"", "\"chid\"", "\"mid\"", "\"dspid\"",
					"\"linkid\"");
		assertThat(JSON.parseObject(clearText, FeedbackReq.class))
			.extracting(FeedbackReq::getMId, FeedbackReq::getTId, FeedbackReq::getAId, FeedbackReq::getLId)
			.containsExactly("media-id", "task-id", "advertiser-id", "link-id");

		UpstreamCallbackRequest callback = new UpstreamCallbackRequest();
		callback.setExt(ext);
		callback.setEvent_type(4);
		callback.setEvent_time(1700000000L);
		CallbackReq result = adapter.toCallback(callback);
		assertThat(result.getActionType()).isEqualTo(ActionTypeEnum.REGISTER);
		assertThat(result.getActionTime()).isEqualTo(1700000000L);
		assertThat(result.getFeedback().getReqId()).isEqualTo("request-1");
	}

	@Test
	void replacesLegacyMacrosAndKeepsTheFixedSignatureVector() {
		TemplateGetProtocolAdapter adapter = adapter();
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-id");
		task.setBizId("BIZ-1");
		monitorTaskIndex.add(task.getId(), task);
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setBidUrl("https://adv.example/click?rid=__REQUEST_ID__&oaid=__OAID__&biz=__BIZ_ID__");
		advertiser.setCredentialConfigKey("advertiser-signing");
		FeedbackReq feedback = FeedbackReq.builder().tId("task-id").reqId("request-1").oaid("oaid-1").build();

		String result = adapter.buildUpstreamRequestUrl(advertiser, feedback);

		assertThat(result).isEqualTo("https://adv.example/click?rid=request-1&oaid=oaid-1&biz=BIZ-1"
				+ "&signature=c3bd8e8cc706ed27f9cff9a0cc0df0fe")
			.doesNotContain("local-signing-key", "advertiser-signing");
	}

	@Test
	void rejectsAnAdvertiserWhoseCredentialReferenceCannotBeResolved() {
		TemplateGetProtocolAdapter adapter = adapter();
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setBidUrl("https://adv.example/click?rid=__REQUEST_ID__");
		advertiser.setCredentialConfigKey("missing-reference");

		assertThat(adapter.buildUpstreamRequestUrl(advertiser, FeedbackReq.builder().reqId("request-1").build()))
			.isNull();
	}

	@Test
	void rejectsCallbackExtThatStillUsesRetiredRouteIds() {
		TemplateGetProtocolAdapter adapter = adapter();
		String retiredJson = "{\"chid\":\"media-id\",\"mid\":\"task-id\","
				+ "\"dspid\":\"advertiser-id\",\"linkid\":\"link-id\",\"reqId\":\"request-1\"}";
		UpstreamCallbackRequest callback = new UpstreamCallbackRequest();
		callback.setExt(AesUtil.encryptBase64(retiredJson, "local-dev-key-01"));
		callback.setEvent_type(4);
		callback.setEvent_time(1700000000L);

		assertThatThrownBy(() -> adapter.toCallback(callback))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("callback ext is missing SCX route ids");
	}

	@Test
	void rejectsCallbackExtWithAnOversizedScxRouteId() {
		TemplateGetProtocolAdapter adapter = adapter();
		for (String oversized : new String[] { "mId", "tId", "aId", "lId" }) {
			FeedbackReq feedback = FeedbackReq.builder()
				.mId("media-id")
				.tId("task-id")
				.aId("advertiser-id")
				.lId("link-id")
				.build();
			switch (oversized) {
				case "mId" -> feedback.setMId("x".repeat(33));
				case "tId" -> feedback.setTId("x".repeat(33));
				case "aId" -> feedback.setAId("x".repeat(33));
				case "lId" -> feedback.setLId("x".repeat(33));
				default -> throw new IllegalStateException("unexpected route id: " + oversized);
			}
			UpstreamCallbackRequest callback = new UpstreamCallbackRequest();
			callback.setExt(AesUtil.encryptBase64(JSON.toJSONString(feedback), "local-dev-key-01"));
			callback.setEvent_type(4);
			callback.setEvent_time(1700000000L);

			assertThatThrownBy(() -> adapter.toCallback(callback))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("callback ext has invalid SCX route id: " + oversized);
		}
	}

	private TemplateGetProtocolAdapter adapter() {
		PartnerCredentialProperties properties = new PartnerCredentialProperties();
		properties.setAdvertisers(Map.of("advertiser-signing", "local-signing-key"));
		TemplateGetProtocolAdapter adapter = new TemplateGetProtocolAdapter(monitorTaskIndex,
				new PartnerCredentialResolver(properties));
		ReflectionTestUtils.setField(adapter, "domainName", "http://127.0.0.1:10000/ocpx");
		ReflectionTestUtils.setField(adapter, "callbackAesKey", "local-dev-key-01");
		return adapter;
	}

}
