package com.yzytech.scx.ocpx.feedback.delivery.gravity;

import com.yzytech.scx.ocpx.callback.service.CallbackDecision;
import com.yzytech.scx.ocpx.callback.service.CallbackHandleResult;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import com.yzytech.scx.ocpx.callback.web.gravity.GravityCallbackAdapter;
import com.yzytech.scx.ocpx.callback.web.gravity.GravityCallbackContextCodec;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GravityUpstreamDeliveryServiceTest {

	private final OcpxPromotionLinkIndex promotionLinkIndex = mock(OcpxPromotionLinkIndex.class);

	private final GravityEndpointPolicy endpointPolicy = mock(GravityEndpointPolicy.class);

	private final GravityHttpClient httpClient = mock(GravityHttpClient.class);

	private final OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);

	private final GravityCallbackContextCodec contextCodec = new GravityCallbackContextCodec(
			"gravity-test-callback-key");

	private GravityUpstreamDeliveryService service;

	@BeforeEach
	void setUp() {
		service = new GravityUpstreamDeliveryService(promotionLinkIndex, endpointPolicy, httpClient, contextCodec,
				flowLogWriter, "https://engine.example.com/ocpx");
		when(endpointPolicy.requireAllowedHttpsUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		when(promotionLinkIndex.get("link-id")).thenReturn(link());
	}

	@Test
	void buildsIosParametersWithIdfaAndEncodesUaAndCallbackExactlyOnce() {
		String url = service.buildRequestUrl(feedback(OsTypeEnum.IOS));

		assertThat(url).startsWith("https://backend.gravity-engine.com/event_center/api/v1/event/click/?fixed=1&")
			.contains("idfa=idfa-value", "ip=203.0.113.10", "ua=Mozilla%2F5.0%20%28iPhone%3B%20Test%29")
			.doesNotContain("iafa=", "oaid=", "android_id=", "imei=", "%252F", "{");
		assertThat(decoded(url, "callback")).startsWith("https://engine.example.com/ocpx/gravity/callback?ctx=");
	}

	@Test
	void buildsAndroidParametersUsingAndroididAndOmitsEmptyIdentifiers() {
		FeedbackReq feedback = feedback(OsTypeEnum.ANDROID);
		feedback.setOaid(null);
		feedback.setImei(null);

		String url = service.buildRequestUrl(feedback);

		assertThat(url).contains("android_id=android-id").doesNotContain("oaid=", "imei=", "idfa=", "{");
	}

	@Test
	void rejectsMissingIdentifiersUnknownOsInvalidIpv4AndDynamicTemplateValues() {
		FeedbackReq ios = feedback(OsTypeEnum.IOS);
		ios.setIdfa(null);
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(ios))
			.withMessage("GRAVITY_IDFA_MISSING");

		FeedbackReq android = feedback(OsTypeEnum.ANDROID);
		android.setOaid(null);
		android.setAndroidid(null);
		android.setImei(null);
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(android))
			.withMessage("GRAVITY_ANDROID_ID_MISSING");

		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(feedback(OsTypeEnum.UNKNOWN)))
			.withMessage("GRAVITY_OS_INVALID");
		FeedbackReq badIp = feedback(OsTypeEnum.IOS);
		badIp.setIp("256.1.1.1");
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(badIp))
			.withMessage("GRAVITY_IPV4_INVALID");

		OcpxPromotionLink template = link();
		template.setLinkUrl("https://backend.gravity-engine.com/event_center/api/v1/event/click/?idfa={idfa}");
		when(promotionLinkIndex.get("link-id")).thenReturn(template);
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(feedback(OsTypeEnum.IOS)))
			.withMessage("GRAVITY_TEMPLATE_MACRO_FORBIDDEN");
	}

	@Test
	void sendsOnlyClicksAndTreatsHttp2xxAsAcceptedTransport() {
		FeedbackReq click = feedback(OsTypeEnum.ANDROID);
		when(httpClient.get(anyString())).thenReturn(new NetUtil.HttpGetResult(204, "", null, 5L));

		service.deliver(click);
		verify(httpClient).get(anyString());

		FeedbackReq impression = feedback(OsTypeEnum.ANDROID);
		impression.setEvent(EventTypeEnum.IMP);
		service.deliver(impression);
		verify(endpointPolicy).requireAllowedHttpsUrl(anyString());
	}

	@Test
	void mockPartnerCaptureRestoresTheOriginalRouteAndEntersTheExistingCallbackService() {
		FeedbackReq click = feedback(OsTypeEnum.IOS);
		when(httpClient.get(anyString())).thenReturn(new NetUtil.HttpGetResult(200, "accepted", null, 4L));

		service.deliver(click);

		var capturedUrl = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(httpClient).get(capturedUrl.capture());
		String callbackUrl = decoded(capturedUrl.getValue(), "callback");
		String token = decoded(callbackUrl, "ctx");
		var callback = new GravityCallbackAdapter(contextCodec).toCallback(token);
		CallbackService callbackService = mock(CallbackService.class);
		when(callbackService.handleCallback(callback))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.FORWARDED));

		assertThat(callbackService.handleCallback(callback).decision()).isEqualTo(CallbackDecision.FORWARDED);
		assertThat(callback.getFeedback().getReqId()).isEqualTo("request-id");
	}

	private OcpxPromotionLink link() {
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-id");
		link.setAdvertiserId("advertiser-id");
		link.setPlatform(3);
		link.setLinkUrl("https://backend.gravity-engine.com/event_center/api/v1/event/click/?fixed=1");
		return link;
	}

	private FeedbackReq feedback(OsTypeEnum os) {
		return FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.event(EventTypeEnum.CLICK)
			.os(os)
			.idfa("idfa-value")
			.oaid("oaid-value")
			.androidid("android-id")
			.imei("imei-value")
			.ip("203.0.113.10")
			.ua("Mozilla/5.0 (iPhone; Test)")
			.callbackUrl("https%3A%2F%2Fmedia.example%2Fcallback%3Frequest_id%3D__REQUEST_ID__")
			.build();
	}

	private String decoded(String url, String key) {
		for (String pair : URI.create(url).getRawQuery().split("&")) {
			int separator = pair.indexOf('=');
			if (separator > 0 && key.equals(pair.substring(0, separator))) {
				return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
			}
		}
		throw new AssertionError("missing query parameter " + key);
	}

}
