package com.yzytech.scx.ocpx.feedback.delivery.aerim;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.web.aerim.AerimCallbackAdapter;
import com.yzytech.scx.ocpx.callback.web.aerim.AerimCallbackRequest;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
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

	private final OcpxPromotionLinkIndex promotionLinkIndex = mock(OcpxPromotionLinkIndex.class);

	private final AerimEndpointPolicy endpointPolicy = mock(AerimEndpointPolicy.class);

	private final AerimHttpClient httpClient = mock(AerimHttpClient.class);

	private final OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);

	private AerimUpstreamDeliveryService service;

	@BeforeEach
	void setUp() {
		service = new AerimUpstreamDeliveryService(promotionLinkIndex, endpointPolicy, httpClient, flowLogWriter);
		ReflectionTestUtils.setField(service, "callbackBaseUrl", "https://engine.test/ocpx");
		ReflectionTestUtils.setField(service, "callbackAesKey", AES_KEY);
		when(endpointPolicy.requireAllowedHttpUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		when(promotionLinkIndex.get("link-id")).thenReturn(promotionLink());
	}

	@Test
	void buildsAndroidUrlOmitsMissingMacrosAndEncodesCallbackExactlyOnce() {
		String url = service.buildRequestUrl(feedback(OsTypeEnum.ANDROID));

		assertThat(url).startsWith("https://track.example.com/ocpx/c?fixed=1&")
			.contains("os=android", "ua=Dalvik%2F2.1%20%28Linux%29", "caid_list=%5B%7B%22version%22", "idfa=idfa-value",
					"idfa_md5=idfa-md5", "imei_md5=imei-md5", "oaid_md5=oaid-md5", "caid=caid-value",
					"android_id=android-id", "android_id_md5=androidid-md5", "aaid=aaid-value", "clickid=click-id",
					"campaign_id=campaign-id", "adgroup_id=group-id", "creative_id=creative-id",
					"advertiser_id=partner-advertiser-id")
			.contains("callback=https%3A%2F%2Fengine.test%2Focpx%2Faerim%2Fcallback%3Fext%3D")
			.doesNotContain("model=", "__", "signature=");
		String callback = decodedQueryValue(url, "callback");
		assertThat(callback).startsWith("https://engine.test/ocpx/aerim/callback?ext=").endsWith("&s=link-id_task-id");
	}

	@Test
	void buildsIosOsValueAndRejectsMissingCallbackOrResidualMacro() {
		FeedbackReq feedback = feedback(OsTypeEnum.IOS);
		assertThat(service.buildRequestUrl(feedback)).contains("os=ios");

		OcpxPromotionLink missingCallback = promotionLink();
		missingCallback.setLinkUrl("https://track.example.com/ocpx/c?ip=__IP__");
		when(promotionLinkIndex.get("link-id")).thenReturn(missingCallback);
		assertThat(service.buildRequestUrl(feedback)).isNull();
		OcpxPromotionLink unknownMacro = promotionLink();
		unknownMacro.setLinkUrl("https://track.example.com/__UNKNOWN__?callback=__CALLBACK__");
		when(promotionLinkIndex.get("link-id")).thenReturn(unknownMacro);
		assertThat(service.buildRequestUrl(feedback)).isNull();
	}

	@Test
	void supportsAnHttpIpCallbackBaseForAerimUat() {
		ReflectionTestUtils.setField(service, "callbackBaseUrl", "http://42.81.102.181/ocpx");

		String callback = decodedQueryValue(service.buildRequestUrl(feedback(OsTypeEnum.IOS)), "callback");

		assertThat(callback).startsWith("http://42.81.102.181/ocpx/aerim/callback?ext=").endsWith("&s=link-id_task-id");
	}

	@Test
	void mockCaptureClosesFeedbackToPurchaseCallbackWithoutRealNetwork() {
		FeedbackReq feedback = feedback(OsTypeEnum.ANDROID);
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
		verify(flowLogWriter).upstreamDelivery(feedback, true, 200, "AERIM_DELIVERED", 7L, "");
	}

	@Test
	void treatsCode402AsFailureAndDoesNotSendNonClickEvents() {
		FeedbackReq click = feedback(OsTypeEnum.ANDROID);
		when(httpClient.get(anyString()))
			.thenReturn(new NetUtil.HttpGetResult(200, "{\"code\":402,\"message\":\"limit\"}", null, 3L));

		service.deliver(click);

		verify(flowLogWriter).upstreamDelivery(click, false, 200, "AERIM_DAILY_LIMIT", 3L,
				"{\"code\":402,\"message\":\"limit\"}");
		FeedbackReq impression = feedback(OsTypeEnum.ANDROID);
		impression.setEvent(EventTypeEnum.IMP);
		service.deliver(impression);
		verify(httpClient).get(anyString());
	}

	@Test
	void recordsNon2xxAndTimeoutWithoutLeakingTheRequestUrl() {
		FeedbackReq click = feedback(OsTypeEnum.ANDROID);
		when(httpClient.get(anyString())).thenReturn(new NetUtil.HttpGetResult(503, "unavailable", null, 9L),
				new NetUtil.HttpGetResult(null, null, "TIMEOUT", 5000L));

		service.deliver(click);
		service.deliver(click);

		verify(flowLogWriter).upstreamDelivery(click, false, 503, "AERIM_HTTP_FAILED", 9L, "unavailable");
		verify(flowLogWriter).upstreamDelivery(click, false, null, "AERIM_TIMEOUT", 5000L, null);
	}

	@Test
	void selectsTheProductUrlByPromotionLinkIdAndAcceptsCallbackUrlAlias() {
		OcpxPromotionLink second = promotionLink();
		second.setId("link-2");
		second.setLinkUrl(
				"https://track.example.com/ocpx/product-two?callback_url=__CALLBACK_URL__&click_id=__CLICK_ID__");
		when(promotionLinkIndex.get("link-2")).thenReturn(second);
		FeedbackReq feedback = feedback(OsTypeEnum.ANDROID);
		feedback.setLId("link-2");

		String url = service.buildRequestUrl(feedback);

		assertThat(url).contains("/ocpx/product-two?", "callback_url=", "click_id=click-id");
	}

	@Test
	void rejectsPromotionLinksOwnedByAnotherAdvertiserAndUnknownQueryMacros() {
		OcpxPromotionLink otherOwner = promotionLink();
		otherOwner.setAdvertiserId("another-advertiser");
		when(promotionLinkIndex.get("link-id")).thenReturn(otherOwner);
		assertThat(service.buildRequestUrl(feedback(OsTypeEnum.ANDROID))).isNull();

		OcpxPromotionLink unknown = promotionLink();
		unknown.setLinkUrl("https://track.example.com/ocpx/c?sid=__UNSUPPORTED__&callback=__CALLBACK__");
		when(promotionLinkIndex.get("link-id")).thenReturn(unknown);
		org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
			.isThrownBy(() -> service.buildRequestUrl(feedback(OsTypeEnum.ANDROID)));
	}

	@Test
	void supportsQutoutiaoIdentityAndTimestampMacrosFromTheProvidedTemplates() {
		OcpxPromotionLink link = promotionLink();
		link.setLinkUrl("https://track.example.com/ocpx/c?sid=__SID__&sid_md5=__SIDMD5__&ip=__IPV4__"
				+ "&ip_md5=__IPMD5__&ts=__TSMS__&callback=__CALLBACK_URL__");
		when(promotionLinkIndex.get("link-id")).thenReturn(link);
		FeedbackReq feedback = feedback(OsTypeEnum.ANDROID);
		feedback.setTs("1723975200");

		String url = service.buildRequestUrl(feedback);

		assertThat(decodedQueryValue(url, "sid")).isEqualTo("oaid-value");
		assertThat(decodedQueryValue(url, "sid_md5")).hasSize(32);
		assertThat(decodedQueryValue(url, "ip_md5")).hasSize(32);
		assertThat(decodedQueryValue(url, "ts")).isEqualTo("1723975200000");
	}

	@Test
	void rejectsMalformedMacrosInsteadOfForwardingTheirLiteralText() {
		OcpxPromotionLink link = promotionLink();
		link.setLinkUrl("https://track.example.com/ocpx/c?oaid_md5=__OAIDMD5&callback=__CALLBACK__");
		when(promotionLinkIndex.get("link-id")).thenReturn(link);

		org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
			.isThrownBy(() -> service.buildRequestUrl(feedback(OsTypeEnum.ANDROID)));
	}

	private OcpxPromotionLink promotionLink() {
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-id");
		link.setAdvertiserId("advertiser-id");
		link.setLinkUrl(
				"https://track.example.com/ocpx/c?fixed=1&ip=__IP__&imei=__IMEI__&imei_md5=__IMEI_MD5__&idfa=__IDFA__"
						+ "&idfa_md5=__IDFA_MD5__&caid=__CAID__&caid_list=__CAID_LIST__&oaid=__OAID__"
						+ "&oaid_md5=__OAID_MD5__"
						+ "&android_id=__ANDROIDID__&android_id_md5=__ANDROIDID_MD5__&aaid=__AAID__"
						+ "&ua=__UA__&os=__OS__&model=__MODEL__&callback=__CALLBACK__"
						+ "&clickid=__CLICKID__&campaign_id=__CAMPAIGNID__&adgroup_id=__ADGROUPID__"
						+ "&creative_id=__CREATIVEID__&advertiser_id=__ADVERTISERID__");
		return link;
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
