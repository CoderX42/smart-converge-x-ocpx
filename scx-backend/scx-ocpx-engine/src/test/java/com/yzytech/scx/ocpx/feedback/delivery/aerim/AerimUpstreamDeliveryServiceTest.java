package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.web.aerim.AerimCallbackAdapter;
import com.yzytech.scx.ocpx.callback.web.aerim.AerimCallbackRequest;
import com.yzytech.scx.ocpx.configuration.index.OcpxAdvertiserIndex;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AerimUpstreamDeliveryServiceTest {

	private static final String AES_KEY = "aerim-test-key16";

	private final OcpxAdvertiserIndex advertiserIndex = mock(OcpxAdvertiserIndex.class);

	private final AerimEndpointPolicy endpointPolicy = mock(AerimEndpointPolicy.class);

	private final AerimHttpClient httpClient = mock(AerimHttpClient.class);

	private final OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);

	private AerimUpstreamDeliveryService service;

	@BeforeEach
	void setUp() {
		service = new AerimUpstreamDeliveryService(advertiserIndex, endpointPolicy, httpClient, flowLogWriter);
		ReflectionTestUtils.setField(service, "callbackBaseUrl", "https://engine.test/ocpx");
		ReflectionTestUtils.setField(service, "callbackAesKey", AES_KEY);
		when(endpointPolicy.requireAllowedHttpUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void buildsAndroidUrlOmitsMissingMacrosAndEncodesCallbackExactlyOnce() {
		String url = service.buildRequestUrl(advertiser(), feedback(OsTypeEnum.ANDROID));

		assertThat(url).startsWith("https://track.example.com/ocpx/c?fixed=1&")
			.contains("os=android", "ua=Dalvik%2F2.1%20%28Linux%29", "caid_list=%5B%7B%22version%22",
					"idfa=idfa-value", "idfa_md5=idfa-md5", "imei_md5=imei-md5", "oaid_md5=oaid-md5",
					"caid=caid-value", "android_id=android-id", "android_id_md5=androidid-md5",
					"aaid=aaid-value", "clickid=click-id", "campaign_id=campaign-id", "adgroup_id=group-id",
					"creative_id=creative-id", "advertiser_id=partner-advertiser-id")
			.contains("callback=https%3A%2F%2Fengine.test%2Focpx%2Faerim%2Fcallback%3Fext%3D")
			.doesNotContain("model=", "__", "signature=");
		String callback = decodedQueryValue(url, "callback");
		assertThat(callback).startsWith("https://engine.test/ocpx/aerim/callback?ext=").endsWith("&s=link-id_task-id");
	}

	@Test
	void buildsIosOsValueAndRejectsMissingCallbackOrResidualMacro() {
		FeedbackReq feedback = feedback(OsTypeEnum.IOS);
		assertThat(service.buildRequestUrl(advertiser(), feedback)).contains("os=ios");

		OcpxAdvertiser missingCallback = advertiser();
		missingCallback.setMacroParams("ip=__IP__");
		assertThat(service.buildRequestUrl(missingCallback, feedback)).isNull();
		OcpxAdvertiser unknownMacro = advertiser();
		unknownMacro.setBidUrl("https://track.example.com/__UNKNOWN__");
		unknownMacro.setMacroParams("callback=__CALLBACK__");
		assertThat(service.buildRequestUrl(unknownMacro, feedback)).isNull();
	}

	@Test
	void supportsAnHttpIpCallbackBaseForAerimUat() {
		ReflectionTestUtils.setField(service, "callbackBaseUrl", "http://42.81.102.181/ocpx");

		String callback = decodedQueryValue(service.buildRequestUrl(advertiser(), feedback(OsTypeEnum.IOS)),
				"callback");

		assertThat(callback).startsWith("http://42.81.102.181/ocpx/aerim/callback?ext=")
			.endsWith("&s=link-id_task-id");
	}

	@Test
	void mockCaptureClosesFeedbackToPurchaseCallbackWithoutRealNetwork() {
		FeedbackReq feedback = feedback(OsTypeEnum.ANDROID);
		when(advertiserIndex.get("advertiser-id")).thenReturn(advertiser());
		when(httpClient.get(anyString())).thenReturn(new NetUtil.HttpGetResult(200, "", null, 7L));

		service.deliver(feedback);

		var url = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(httpClient).get(url.capture());
		String callbackUrl = decodedQueryValue(url.getValue(), "callback");
		AerimCallbackRequest callbackRequest = new AerimCallbackRequest();
		callbackRequest.setExt(decodedQueryValue(callbackUrl, "ext"));
		callbackRequest.setS(decodedQueryValue(callbackUrl, "s"));
		callbackRequest.setAction_type("purchase");
		callbackRequest.setCurrency_amount(new BigDecimal("123.45"));
		AerimCallbackAdapter adapter = new AerimCallbackAdapter();
		ReflectionTestUtils.setField(adapter, "callbackAesKey", AES_KEY);

		CallbackReq callback = adapter.toCallback(callbackRequest);

		assertThat(callback.getActionType()).isEqualTo(ActionTypeEnum.PAYMENT);
		assertThat(callback.getCurrencyAmount()).isEqualByComparingTo("123.45");
		assertThat(callback.getFeedback().getReqId()).isEqualTo("request-id");
		verify(flowLogWriter).upstreamDelivery(feedback, true, 200, "AERIM_DELIVERED", 7L);
	}

	@Test
	void treatsCode402AsFailureAndDoesNotSendNonClickEvents() {
		FeedbackReq click = feedback(OsTypeEnum.ANDROID);
		when(advertiserIndex.get("advertiser-id")).thenReturn(advertiser());
		when(httpClient.get(anyString()))
			.thenReturn(new NetUtil.HttpGetResult(200, "{\"code\":402,\"message\":\"limit\"}", null, 3L));

		service.deliver(click);

		verify(flowLogWriter).upstreamDelivery(click, false, 200, "AERIM_DAILY_LIMIT", 3L);
		FeedbackReq impression = feedback(OsTypeEnum.ANDROID);
		impression.setEvent(EventTypeEnum.IMP);
		service.deliver(impression);
		verify(httpClient).get(anyString());
	}

	@Test
	void recordsNon2xxAndTimeoutWithoutLeakingTheRequestUrl() {
		FeedbackReq click = feedback(OsTypeEnum.ANDROID);
		when(advertiserIndex.get("advertiser-id")).thenReturn(advertiser());
		when(httpClient.get(anyString())).thenReturn(new NetUtil.HttpGetResult(503, "unavailable", null, 9L),
				new NetUtil.HttpGetResult(null, null, "TIMEOUT", 5000L));

		service.deliver(click);
		service.deliver(click);

		verify(flowLogWriter).upstreamDelivery(click, false, 503, "AERIM_HTTP_FAILED", 9L);
		verify(flowLogWriter).upstreamDelivery(click, false, null, "AERIM_TIMEOUT", 5000L);
	}

	private OcpxAdvertiser advertiser() {
		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setBidUrl("https://track.example.com/ocpx/c?fixed=1");
		advertiser.setMacroParams("ip=__IP__&imei=__IMEI__&imei_md5=__IMEI_MD5__&idfa=__IDFA__"
				+ "&idfa_md5=__IDFA_MD5__&caid=__CAID__&caid_list=__CAID_LIST__&oaid=__OAID__"
				+ "&oaid_md5=__OAID_MD5__"
				+ "&android_id=__ANDROIDID__&android_id_md5=__ANDROIDID_MD5__&aaid=__AAID__"
				+ "&ua=__UA__&os=__OS__&model=__MODEL__&callback=__CALLBACK__"
				+ "&clickid=__CLICKID__&campaign_id=__CAMPAIGNID__&adgroup_id=__ADGROUPID__"
				+ "&creative_id=__CREATIVEID__&advertiser_id=__ADVERTISERID__");
		return advertiser;
	}

	private FeedbackReq feedback(OsTypeEnum os) {
		return FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.event(EventTypeEnum.CLICK)
			.reqId("request-id")
			.clickId("click-id")
			.imei("imei-value")
			.imeiMd5("imei-md5")
			.idfa("idfa-value")
			.idfaMd5("idfa-md5")
			.oaid("oaid-value")
			.oaidMd5("oaid-md5")
			.caid("caid-value")
			.androidid("android-id")
			.androididMd5("androidid-md5")
			.aaid("aaid-value")
			.caidList("[{\"version\":\"20220111\",\"caid\":\"caid-value\"}]")
			.ip("203.0.113.10")
			.ua("Dalvik/2.1 (Linux)")
			.os(os)
			.planId("campaign-id")
			.groupId("group-id")
			.creativeId("creative-id")
			.advId("partner-advertiser-id")
			.build();
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
