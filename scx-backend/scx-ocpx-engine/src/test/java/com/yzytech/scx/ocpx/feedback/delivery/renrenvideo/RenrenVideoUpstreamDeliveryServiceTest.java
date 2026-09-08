package com.yzytech.scx.ocpx.feedback.delivery.renrenvideo;

import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.configuration.index.OcpxPromotionLinkIndex;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.feedback.delivery.MacroResolutionService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.OsTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.DigestUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenrenVideoUpstreamDeliveryServiceTest {

	private final OcpxPromotionLinkIndex promotionLinkIndex = mock(OcpxPromotionLinkIndex.class);

	private final RenrenVideoEndpointPolicy endpointPolicy = mock(RenrenVideoEndpointPolicy.class);

	private final RenrenVideoHttpClient httpClient = mock(RenrenVideoHttpClient.class);

	private final OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);

	private final OcpxKeyRingCrypto callbackCrypto = new OcpxKeyRingCrypto("v1", Map.of("v1", "renren-video-test-key"),
			List.of("v1"));

	private RenrenVideoUpstreamDeliveryService service;

	@BeforeEach
	void setUp() {
		service = new RenrenVideoUpstreamDeliveryService(promotionLinkIndex, endpointPolicy, httpClient, callbackCrypto,
				flowLogWriter, new MacroResolutionService(), "https://engine.example.com/ocpx");
		when(endpointPolicy.requireAllowedHttpUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void buildsStrictAndroidUrlWithDerivedMd5TimestampClickIdAndPassThrough() {
		OcpxPromotionLink link = androidLink();
		when(promotionLinkIndex.get("link-id")).thenReturn(link);
		FeedbackReq feedback = androidFeedback();

		String url = service.buildRequestUrl(feedback);

		assertThat(decoded(url, "imeiMd5")).isEqualTo(md5("123456789012345"));
		assertThat(decoded(url, "oaidMd5")).isEqualTo(md5("0123456789abcdef0123456789abcdef"));
		assertThat(decoded(url, "androidIdMd5")).isEqualTo(md5("abcdef0123456789"));
		assertThat(decoded(url, "ts")).isEqualTo("1704038400000");
		assertThat(decoded(url, "clickId")).isEqualTo("request-id_1704038400000");
		assertThat(decoded(url, "passThrough")).isEqualTo("package-a");
		assertThat(decoded(url, "callback"))
			.startsWith("https://engine.example.com/ocpx/renren-video/callback?kid=v1&ext=");
		// Base64url callback ciphertext may legitimately contain consecutive underscores.
		assertThat(url).doesNotContain("%252F").doesNotContainPattern("__[A-Z][A-Z0-9_]*__");
	}

	@Test
	void buildsIosUrlWithSecondTimestampAndDerivedIdfaMd5() {
		OcpxPromotionLink link = iosLink();
		when(promotionLinkIndex.get("link-id")).thenReturn(link);
		FeedbackReq feedback = iosFeedback();

		String url = service.buildRequestUrl(feedback);

		assertThat(decoded(url, "ts")).isEqualTo("1704038400");
		assertThat(decoded(url, "idfa")).isEqualTo("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE");
		assertThat(decoded(url, "idfaMd5")).isEqualTo(md5("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"));
	}

	@Test
	void rejectsMissingStrictAndroidValuesPlatformMismatchAndMissingIosIdentity() {
		when(promotionLinkIndex.get("link-id")).thenReturn(androidLink());
		FeedbackReq missingOaid = androidFeedback();
		missingOaid.setOaid(null);
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(missingOaid))
			.withMessage("RENREN_VIDEO_OAID_MISSING");

		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(iosFeedback()))
			.withMessage("RENREN_VIDEO_OS_PLATFORM_MISMATCH");

		when(promotionLinkIndex.get("link-id")).thenReturn(iosLink());
		FeedbackReq missingIosIdentity = iosFeedback();
		missingIosIdentity.setIdfa(null);
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(missingIosIdentity))
			.withMessage("RENREN_VIDEO_IOS_IDENTITY_MISSING");
	}

	@ParameterizedTest
	@CsvSource({ "200,RENREN_VIDEO_DELIVERED", "201,RENREN_VIDEO_INVALID_USER_ID", "202,RENREN_VIDEO_BUDGET_EXHAUSTED",
			"203,RENREN_VIDEO_BALANCE_INSUFFICIENT", "204,RENREN_VIDEO_REQUIRED_PARAM_MISSING",
			"205,RENREN_VIDEO_PARAM_TOO_LONG", "300,RENREN_VIDEO_SYSTEM_ERROR", "999,RENREN_VIDEO_BUSINESS_ERROR" })
	void mapsAllPartnerBusinessCodes(int code, String expected) {
		assertThat(service.conclusion(200, code, null)).isEqualTo(expected);
	}

	@Test
	void requiresBothHttpAndBusinessSuccessAndMapsInvalidResponses() {
		assertThat(service.conclusion(404, 200, null)).isEqualTo("RENREN_VIDEO_HTTP_NOT_FOUND");
		assertThat(service.conclusion(500, 200, null)).isEqualTo("RENREN_VIDEO_HTTP_SERVER_ERROR");
		assertThat(service.conclusion(200, null, null)).isEqualTo("RENREN_VIDEO_RESPONSE_INVALID");
		assertThat(service.conclusion(null, null, "TIMEOUT")).isEqualTo("RENREN_VIDEO_TIMEOUT");
	}

	private OcpxPromotionLink androidLink() {
		OcpxPromotionLink link = link(1);
		link.setLinkExt("package-a");
		link.setLinkUrl(common("__TS__") + "&imeiMd5=__IMEI__&oaid=__OAID__&oaidMd5=__OAID_MD5__"
				+ "&androidId=__ANDROID_ID__&androidIdMd5=__ANDROID_ID_MD5__&passThrough=__PASS_THROUGH__");
		return link;
	}

	private OcpxPromotionLink iosLink() {
		OcpxPromotionLink link = link(2);
		link.setLinkUrl(common("__TS_S__") + "&idfa=__IDFA__&idfaMd5=__IDFA_MD5__");
		return link;
	}

	private OcpxPromotionLink link(int platform) {
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-id");
		link.setAdvertiserId("advertiser-id");
		link.setPlatform(platform);
		return link;
	}

	private String common(String timestampMacro) {
		return "http://monitor.example.com/ad-action/click?userId=123&ts=" + timestampMacro
				+ "&ip=__IP__&os=__OS__&ua=__UA__&model=__MODEL__&callback=__CALLBACK__"
				+ "&requestId=__REQID__&clickId=__CLICK_ID__";
	}

	private FeedbackReq androidFeedback() {
		return base(OsTypeEnum.ANDROID).imei("123456789012345")
			.oaid("0123456789abcdef0123456789abcdef")
			.androidid("abcdef0123456789")
			.build();
	}

	private FeedbackReq iosFeedback() {
		return base(OsTypeEnum.IOS).idfa("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE").build();
	}

	private FeedbackReq.FeedbackReqBuilder base(OsTypeEnum os) {
		return FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.event(EventTypeEnum.CLICK)
			.os(os)
			.ip("203.0.113.10")
			.ua("Mozilla/5.0 (Test Device)")
			.model("Test Model")
			.ts("1704038400")
			.callbackUrl("https%3A%2F%2Fmedia.example%2Fcallback");
	}

	private String decoded(String url, String key) {
		for (String pair : URI.create(url).getRawQuery().split("&")) {
			int separator = pair.indexOf('=');
			if (separator > 0 && key.equals(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8))) {
				return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
			}
		}
		throw new AssertionError("missing query parameter " + key);
	}

	private String md5(String value) {
		return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
	}

}
