package com.yzytech.scx.ocpx.feedback.delivery.weifeng;

import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.configuration.index.OcpxMonitorTaskIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeifengUpstreamDeliveryServiceTest {

	private static final String AES_KEY = "weifeng-test-key";

	private final OcpxAdvertiserIndex advertiserIndex = mock(OcpxAdvertiserIndex.class);

	private final OcpxMonitorTaskIndex monitorTaskIndex = mock(OcpxMonitorTaskIndex.class);

	private final WeifengProperties properties = new WeifengProperties();

	private final WeifengEndpointPolicy endpointPolicy = mock(WeifengEndpointPolicy.class);

	private final WeifengHttpClient httpClient = mock(WeifengHttpClient.class);

	private final OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);

	private WeifengUpstreamDeliveryService service;

	@BeforeEach
	void setUp() {
		properties.setMediaIds(Map.of("media-id", "yz"));
		service = new WeifengUpstreamDeliveryService(advertiserIndex, monitorTaskIndex, properties, endpointPolicy,
				httpClient, flowLogWriter);
		ReflectionTestUtils.setField(service, "callbackBaseUrl", "https://engine.test/ocpx");
		ReflectionTestUtils.setField(service, "callbackAesKey", AES_KEY);
		when(advertiserIndex.get("advertiser-id")).thenReturn(advertiser());
		when(monitorTaskIndex.get("task-id")).thenReturn(task());
		when(endpointPolicy.requireAllowedClickUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void buildsAndroidV13ClickUrlAndEncodesValuesExactlyOnce() {
		String requestUrl = service.buildRequestUrl(androidFeedback());

		assertThat(requestUrl).startsWith("https://ad.adapi.cn/click?").doesNotContain("caid1=", "caid2=");
		assertThat(decodedQueryValue(requestUrl, "oaid")).isEqualTo("oaid-value");
		assertThat(decodedQueryValue(requestUrl, "androidid")).isEqualTo("android-id");
		assertThat(decodedQueryValue(requestUrl, "ipv6")).isEqualTo("2408:1::8");
		assertThat(decodedQueryValue(requestUrl, "ua")).isEqualTo("Dalvik/2.1 (Linux)");
		assertThat(decodedQueryValue(requestUrl, "ts")).isEqualTo("1700000000000");
		assertThat(decodedQueryValue(requestUrl, "os")).isEqualTo("ANDROID");
		assertThat(decodedQueryValue(requestUrl, "osv")).isEqualTo("13.1.1");
		assertThat(decodedQueryValue(requestUrl, "model")).isEqualTo("KNT-AL20");
		assertThat(decodedQueryValue(requestUrl, "aid")).isEqualTo("4946");
		assertThat(decodedQueryValue(requestUrl, "did")).isEqualTo("yz");
		assertThat(decodedQueryValue(requestUrl, "callback"))
			.startsWith("https://engine.test/ocpx/weifeng/callback?ext=")
			.contains("&s=link-id_task-id&event=__EVENT__&amount=__AMOUNT__&days=__DAYS__");
	}

	@Test
	void buildsIosCaidVersionsAndConvertsSecondsTimestamp() {
		FeedbackReq feedback = baseFeedback(OsTypeEnum.IOS);
		feedback.setCaid1("new-caid_20260506");
		feedback.setCaid2("old-caid_20250325");
		feedback.setTsMs(null);
		feedback.setTs("1700000000");

		String requestUrl = service.buildRequestUrl(feedback);

		assertThat(decodedQueryValue(requestUrl, "caid1")).isEqualTo("new-caid_20260506");
		assertThat(decodedQueryValue(requestUrl, "caid2")).isEqualTo("old-caid_20250325");
		assertThat(decodedQueryValue(requestUrl, "ts")).isEqualTo("1700000000000");
		assertThat(decodedQueryValue(requestUrl, "os")).isEqualTo("IOS");
	}

	@Test
	void rejectsMissingPartnerMappingRequiredFieldsAndAttributionIdentity() {
		FeedbackReq feedback = baseFeedback(OsTypeEnum.ANDROID);

		assertThat(service.buildRequestUrl(feedback)).isNull();
		feedback.setIp("203.0.113.8");
		feedback.setUa("ua");
		assertThat(service.buildRequestUrl(feedback)).isNotNull();

		properties.setMediaIds(Map.of());
		assertThat(service.buildRequestUrl(feedback)).isNull();
	}

	@Test
	void recordsDocumentedHttpStatusesAndTransportFailure() {
		FeedbackReq feedback = androidFeedback();
		when(httpClient.get(anyString())).thenReturn(new NetUtil.HttpGetResult(200, "", null, 3L),
				new NetUtil.HttpGetResult(402, "", null, 4L), new NetUtil.HttpGetResult(501, "", null, 5L),
				new NetUtil.HttpGetResult(null, null, "TIMEOUT", 3000L));

		service.deliver(feedback);
		service.deliver(feedback);
		service.deliver(feedback);
		service.deliver(feedback);

		verify(flowLogWriter).upstreamDelivery(feedback, true, 200, "WEIFENG_DELIVERED", 3L);
		verify(flowLogWriter).upstreamDelivery(feedback, false, 402, "WEIFENG_CAMPAIGN_PAUSED", 4L);
		verify(flowLogWriter).upstreamDelivery(feedback, false, 501, "WEIFENG_DUPLICATE_CLICK", 5L);
		verify(flowLogWriter).upstreamDelivery(feedback, false, null, "WEIFENG_TIMEOUT", 3000L);
	}

	@Test
	void ignoresNonClickEvents() {
		FeedbackReq feedback = androidFeedback();
		feedback.setEvent(EventTypeEnum.IMP);

		service.deliver(feedback);

		verify(httpClient, never()).get(anyString());
	}

	private FeedbackReq androidFeedback() {
		FeedbackReq feedback = baseFeedback(OsTypeEnum.ANDROID);
		feedback.setOaid("oaid-value");
		feedback.setImei("imei-value");
		feedback.setAndroidid("android-id");
		feedback.setIpv6("2408:1::8");
		feedback.setUa("Dalvik/2.1 (Linux)");
		feedback.setOsv("13.1.1");
		return feedback;
	}

	private FeedbackReq baseFeedback(OsTypeEnum os) {
		return FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.event(EventTypeEnum.CLICK)
			.reqId("request-id")
			.os(os)
			.tsMs("1700000000000")
			.model("KNT-AL20")
			.build();
	}

	private OcpxAdvertiser advertiser() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setBidUrl("https://ad.adapi.cn/click");
		return advertiser;
	}

	private OcpxMonitorTask task() {
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setBizId("4946");
		return task;
	}

	private String decodedQueryValue(String url, String key) {
		String query = URI.create(url).getRawQuery();
		for (String pair : query.split("&")) {
			int separator = pair.indexOf('=');
			if (separator > 0 && key.equals(pair.substring(0, separator))) {
				return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
			}
		}
		throw new AssertionError("missing query parameter " + key);
	}

}
