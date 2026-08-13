package com.yzytech.scx.ocpx.callback.delivery.mi;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialProperties;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialResolver;
import com.yzytech.scx.ocpx.configuration.index.OcpxMediaIndex;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.util.NetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

class MiDownstreamDeliveryServiceTest {

	private final OcpxMediaIndex mediaIndex = new OcpxMediaIndex();

	private MiDownstreamDeliveryService service;

	@BeforeEach
	void setUp() {
		PartnerCredentialProperties properties = new PartnerCredentialProperties();
		properties.setMedia(Map.of("mi-signing", "mi-secret"));
		service = new MiDownstreamDeliveryService(mediaIndex, new PartnerCredentialResolver(properties));
	}

	@AfterEach
	void clearIndex() {
		mediaIndex.clear();
	}

	@Test
	void buildsSignedCallbackUsingServerSideMediaCredentialReference() {
		media("media-id", "mi-signing");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			netUtil.when(() -> NetUtil.httpGet(any(), isNull())).thenReturn("{\"code\":0}");

			service.deliver(request(ActionTypeEnum.ACTIVATE, "imei-1", "oaid-1"));

			String params = "callback=mi-ext&imei=imei-1&conv_time=1700000000123&convType=APP_ACTIVE";
			String sign = DigestUtils.md5DigestAsHex((params + "mi-secret").getBytes(StandardCharsets.UTF_8));
			String expected = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign
					+ "&event_type=APP_ACTIVE";
			netUtil.verify(() -> NetUtil.httpGet(expected, null));
		}
	}

	@Test
	void fallsBackToOaidWhenImeiIsMissing() {
		media("media-id", "mi-signing");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			service.deliver(request(ActionTypeEnum.REGISTER, null, "oaid-1"));

			String params = "callback=mi-ext&oaid=oaid-1&conv_time=1700000000123&convType=APP_REGISTER";
			String sign = DigestUtils.md5DigestAsHex((params + "mi-secret").getBytes(StandardCharsets.UTF_8));
			String expected = "http://trail.e.mi.com/api/callback?" + params + "&sign=" + sign
					+ "&event_type=APP_REGISTER";
			netUtil.verify(() -> NetUtil.httpGet(expected, null));
		}
	}

	@Test
	void refusesDeliveryWhenCredentialReferenceCannotBeResolved() {
		media("media-id", "missing-reference");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			service.deliver(request(ActionTypeEnum.PAYMENT, "imei-1", null));
			netUtil.verifyNoInteractions();
		}
	}

	@Test
	void skipsUnsupportedActions() {
		media("media-id", "mi-signing");
		try (MockedStatic<NetUtil> netUtil = Mockito.mockStatic(NetUtil.class)) {
			service.deliver(request(ActionTypeEnum.LOGIN, "imei-1", null));
			service.deliver(request(ActionTypeEnum.NEXT_STAY, "imei-1", null));
			netUtil.verifyNoInteractions();
		}
	}

	private void media(String id, String credentialReference) {
		OcpxMedia media = new OcpxMedia();
		media.setId(id);
		media.setCredentialConfigKey(credentialReference);
		mediaIndex.add(id, media);
	}

	private CallbackReq request(ActionTypeEnum actionType, String imei, String oaid) {
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.imei(imei)
			.oaid(oaid)
			.ext("mi-ext")
			.build();
		return CallbackReq.builder().feedback(feedback).actionType(actionType).actionTime(1700000000123L).build();
	}

}
