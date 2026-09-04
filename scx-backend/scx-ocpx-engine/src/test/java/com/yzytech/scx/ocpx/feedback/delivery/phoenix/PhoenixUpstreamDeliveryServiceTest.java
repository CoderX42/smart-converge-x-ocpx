package com.yzytech.scx.ocpx.feedback.delivery.phoenix;

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

class PhoenixUpstreamDeliveryServiceTest {

	private final OcpxPromotionLinkIndex linkIndex = mock(OcpxPromotionLinkIndex.class);

	private final PhoenixEndpointPolicy endpointPolicy = mock(PhoenixEndpointPolicy.class);

	private final PhoenixHttpClient httpClient = mock(PhoenixHttpClient.class);

	private final OcpxFlowLogWriter flowLogWriter = mock(OcpxFlowLogWriter.class);

	private final OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("v1", Map.of("v1", "phoenix-test-key"), List.of("v1"));

	private PhoenixUpstreamDeliveryService service;

	@BeforeEach
	void setUp() {
		service = new PhoenixUpstreamDeliveryService(linkIndex, endpointPolicy, httpClient, crypto, flowLogWriter,
				new MacroResolutionService(), "https://engine.example.com/ocpx");
		when(endpointPolicy.requireAllowedHttpsUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void buildsAndroidClickAndImpressionUsingPdfPathsAndUppercaseMd5() {
		when(linkIndex.get("link-id")).thenReturn(link(1));
		FeedbackReq feedback = base(EventTypeEnum.CLICK, OsTypeEnum.ANDROID).oaid("oaid-value").ts("1704038400").build();

		String click = service.buildRequestUrl(feedback);
		assertThat(URI.create(click).getPath()).isEqualTo("/d/cli");
		assertThat(decoded(click, "os")).isEqualTo("0");
		assertThat(decoded(click, "ts")).isEqualTo("1704038400000");
		assertThat(decoded(click, "oaid_md5")).isEqualTo("D22646449410711650419E4BAC150402");
		assertThat(decoded(click, "callback")).startsWith("https://engine.example.com/ocpx/phoenix/callback?kid=v1&ext=");

		feedback.setEvent(EventTypeEnum.IMP);
		String impression = service.buildRequestUrl(feedback);
		assertThat(URI.create(impression).getPath()).isEqualTo("/d/imp");
		assertThat(impression).doesNotContain("callback=");
	}

	@Test
	void buildsIosUrlWithCaidAndOsOne() {
		when(linkIndex.get("link-id")).thenReturn(link(2));
		FeedbackReq feedback = base(EventTypeEnum.CLICK, OsTypeEnum.IOS).caidList("CAID-V1,CAID-V2")
				.idfa("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE").build();

		String url = service.buildRequestUrl(feedback);
		assertThat(decoded(url, "os")).isEqualTo("1");
		assertThat(decoded(url, "caid")).isEqualTo("CAID-V1,CAID-V2");
	}

	@Test
	void keepsSuppliedClickPathWithoutChangingImpressionEndpoint() {
		when(linkIndex.get("link-id")).thenReturn(link(1, "/ocpx/d/cli"));
		FeedbackReq feedback = base(EventTypeEnum.CLICK, OsTypeEnum.ANDROID).oaid("oaid-value").build();

		assertThat(URI.create(service.buildRequestUrl(feedback)).getPath()).isEqualTo("/ocpx/d/cli");

		feedback.setEvent(EventTypeEnum.IMP);
		assertThat(URI.create(service.buildRequestUrl(feedback)).getPath()).isEqualTo("/d/imp");
	}

	@Test
	void rejectsPlatformIdentityMismatchAndInvalidTimestamp() {
		when(linkIndex.get("link-id")).thenReturn(link(1));
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(
				base(EventTypeEnum.CLICK, OsTypeEnum.IOS).oaid("oaid-value").build())).withMessage("PHOENIX_OS_INVALID");
		FeedbackReq missingOaid = base(EventTypeEnum.CLICK, OsTypeEnum.ANDROID).ts("bad").build();
		assertThatIllegalArgumentException().isThrownBy(() -> service.buildRequestUrl(missingOaid))
				.withMessage("PHOENIX_TIMESTAMP_INVALID");
	}

	private OcpxPromotionLink link(int platform) {
		return link(platform, "/d/cli");
	}

	private OcpxPromotionLink link(int platform, String path) {
		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-id");
		link.setAdvertiserId("advertiser-id");
		link.setPlatform(platform);
		link.setLinkUrl("https://api.feather.ifeng.com" + path + "?cid=123&gid=456&aid=789&mid=999&channel=abc"
				+ "&reqid=__REQID__&os=__OS__&ts=__TS__&callback=__CALLBACK__"
				+ (platform == 1 ? "&oaid=__OAID__&oaid_md5=__OAIDMD5__" : "&caid=__CAID__&idfa=__IDFA__"));
		return link;
	}

	private FeedbackReq.FeedbackReqBuilder base(EventTypeEnum event, OsTypeEnum os) {
		return FeedbackReq.builder().mId("media-id").tId("task-id").aId("advertiser-id").lId("link-id")
				.reqId("request-id").event(event).os(os).ts("1704038400");
	}

	private String decoded(String url, String name) {
		for (String pair : URI.create(url).getRawQuery().split("&")) {
			int separator = pair.indexOf('=');
			if (separator > 0 && name.equals(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8))) {
				return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

}
