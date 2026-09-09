package com.yzytech.scx.ocpx.oppo;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.yzytech.scx.ocpx.rta.relay.*;
import com.yzytech.scx.ocpx.rta.contract.RtaContract.Media;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialProperties;
import com.yzytech.scx.ocpx.configuration.credential.PartnerCredentialProperties.OppoUpload;
import com.yzytech.scx.ocpx.oppo.callback.OppoUploadActiveDataDeliveryService;
import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.junit.jupiter.api.Test;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OppoProtocolTest {

	static final String SECRET = "local-test-oppo-secret-0123456789012345";
	static final long NOW = 1788912000000L;

	OppoRtaService service() {
		RelayProperties properties = new RelayProperties();
		properties.setCredentials(Map.of("oppo", SECRET));
		return new OppoRtaService(mock(RelayRepository.class), mock(RelayService.class), mock(RelayStore.class),
				new RelaySecurity(properties, null), mock(RelayAudit.class));
	}

	Media media(Map<String, String> config) {
		return new Media("m", "", "oppo", 1, true, "OPPO", config);
	}

	Map<String, Object> request() {
		var values = new LinkedHashMap<String, Object>();
		values.put("request_id", "request-1");
		values.put("oaid", "device-a");
		values.put("request_time", NOW);
		values.put("channel_name", "OPPO");
		values.put("ad_type_list", List.of(452, 453));
		values.put("sign", OppoRtaService.md5("request-1OPPO" + NOW + SECRET));
		return values;
	}

	JsonNode node(Map<String, Object> body) {
		return RelayJson.read(JSON.toJSONString(body), JsonNode.class);
	}

	@Test
	void acceptsDocumentSignatureAndExplicitOwnerModes() {
		var input = service().parse(node(request()), media(Map.of()), NOW);
		assertThat(input.strategies()).containsExactly(452, 453);
		var b = request();
		b.put("owner_id", 123);
		b.put("sign", OppoRtaService.md5("request-1123OPPO" + NOW + SECRET));
		assertThat(service().parse(node(b), media(Map.of("ownerMode", "VALUE", "ownerId", "123")), NOW).owner())
			.isEqualTo("123");
		assertThatThrownBy(() -> service().parse(node(b), media(Map.of()), NOW)).hasMessage("OPPO_OWNER_INVALID");
	}

	@Test
	void rejectsBadSignatureTimeTypesAndDuplicateStrategies() {
		var b = request();
		b.put("sign", "0".repeat(32));
		var bad = b;
		assertThatThrownBy(() -> service().parse(node(bad), media(Map.of()), NOW)).hasMessage("OPPO_SIGNATURE_INVALID");
		assertThatThrownBy(() -> service().parse(node(request()), media(Map.of()), NOW + 300001))
			.hasMessage("OPPO_SIGNATURE_EXPIRED");
		b = request();
		b.put("ad_type_list", List.of(452, 452));
		var duplicate = b;
		assertThatThrownBy(() -> service().parse(node(duplicate), media(Map.of()), NOW))
			.hasMessage("OPPO_STRATEGIES_INVALID");
		b = request();
		b.put("ad_type_list", List.of(2147483648L));
		var overflow = b;
		assertThatThrownBy(() -> service().parse(node(overflow), media(Map.of()), NOW))
			.hasMessage("OPPO_STRATEGIES_INVALID");
	}

	CallbackReq callback(ActionTypeEnum action) {
		FeedbackReq f = FeedbackReq.builder()
			.mId("m")
			.tId("t")
			.reqId("click-id")
			.creativeId("987654321")
			.oaidMd5(OppoRtaService.md5("device-a"))
			.build();
		f.setDownstreamProtocol("OPPO");
		f.setDownstreamCredentialRef("upload");
		f.setOppoPkg("com.example.app");
		f.setRtaProtected(true);
		return CallbackReq.builder().feedback(f).actionTime(NOW - 1000).actionType(action).build();
	}

	OppoUpload credential() {
		var c = new OppoUpload();
		c.setSalt("local-salt");
		c.setAesKey(Base64.getEncoder().encodeToString(new byte[16]));
		return c;
	}

	@Test
	void uploadEncryptsHashSignsExactBytesAndMapsAppPayment() throws Exception {
		var c = credential();
		c.setOwnerId("123");
		c.setApiId("api-test");
		c.setApiKey("local-api-key");
		var upload = OppoUploadActiveDataDeliveryService.build(callback(ActionTypeEnum.PAYMENT), c, NOW);
		var body = JSON.parseObject(upload.body());
		assertThat(body.getIntValue("dataType")).isEqualTo(7);
		assertThat(body.getIntValue("userIdType")).isEqualTo(1);
		assertThat(body.getIntValue("type")).isEqualTo(2);
		assertThat(body.getLongValue("timestamp")).isEqualTo(NOW - 1000);
		assertThat(body.get("adId")).isInstanceOf(Number.class);
		assertThat(body).doesNotContainKeys("keyTrackInfo", "payId", "payAmount");
		assertThat(upload.headers().get("signature")).isEqualTo(OppoRtaService.md5(upload.body() + NOW + c.getSalt()));
		assertThat(new String(Base64.getDecoder().decode(upload.headers().get("ownerToken")), StandardCharsets.UTF_8))
			.startsWith("123,api-test," + NOW / 1000 + ",");
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(new byte[16], "AES"));
		assertThat(
				new String(cipher.doFinal(Base64.getDecoder().decode(body.getString("ouId"))), StandardCharsets.UTF_8))
			.isEqualTo(OppoRtaService.md5("device-a"));
	}

	@Test
	void protectedCallbackKeepsMediaContextButNotRawDeviceOrClickEnvelope() {
		var f = callback(ActionTypeEnum.ACTIVATE).getFeedback();
		f.setOaid("raw-device");
		f.setOppoClickDevice("encrypted-transient");
		var copy = f.copyForRtaDownstream();
		String json = JSON.toJSONString(copy);
		assertThat(json).doesNotContain("raw-device", "encrypted-transient");
		var restored = JSON.parseObject(json, FeedbackReq.class);
		assertThat(restored.getDownstreamProtocol()).isEqualTo("OPPO");
		assertThat(restored.getOppoPkg()).isEqualTo("com.example.app");
		assertThat(restored.getDownstreamCredentialRef()).isEqualTo("upload");
		assertThat(restored.getOaidMd5()).isEqualTo(f.getOaidMd5());
	}

	@Test
	void http200WithBusinessFailureIsNotSuccess() {
		var props = new PartnerCredentialProperties();
		props.setOppoUpload(Map.of("upload", credential()));
		var transport = mock(RelayHttpTransport.class);
		var logs = mock(OcpxFlowLogWriter.class);
		when(transport.uploadOppo(anyString(), anyMap()))
			.thenReturn(new RtaUpstreamAdapter.HttpResult(200, "{\"ret\":1008,\"msg\":\"owner token invalid\"}"));
		var cb = callback(ActionTypeEnum.REGISTER);
		new OppoUploadActiveDataDeliveryService(props, transport, logs).deliver(cb);
		verify(logs).downstreamDelivery(eq(cb), eq(false), eq(200), eq("OPPO_REJECTED"), anyLong(), isNull(), isNull());
	}

	@Test
	void httpEndpointReturnsWireShapeAndRejectsOversizeBeforeDecision() throws Exception {
		var svc = mock(OppoRtaService.class);
		var m = media(Map.of());
		var input = service().parse(node(request()), m, NOW);
		when(svc.media("oppo")).thenReturn(m);
		when(svc.parse(any(), eq(m), anyLong())).thenReturn(input);
		when(svc.decide(eq(m), eq(input), anyLong()))
			.thenReturn(new OppoRtaService.Response(0, "", new OppoRtaService.Data("request-1", List.of(452))));
		var controller = new OppoRtaController(svc);
		try {
			var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
			var pending = mvc
				.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/rta/oppo/oppo")
					.contentType("application/json")
					.content(JSON.toJSONString(request())))
				.andReturn();
			pending.getAsyncResult(2000);
			var result = mvc
				.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(pending))
				.andReturn()
				.getResponse();
			assertThat(result.getStatus()).isEqualTo(200);
			var body = JSON.parseObject(result.getContentAsString());
			assertThat(body.getIntValue("ret")).isZero();
			assertThat(body.getJSONObject("data").getString("id")).isEqualTo("request-1");
			assertThat(body.getJSONObject("data").getList("agree_type_list", Integer.class)).containsExactly(452);
			assertThat(mvc
				.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/rta/oppo/oppo")
					.contentType("application/json")
					.content(" ".repeat(65537)))
				.andReturn()
				.getResponse()
				.getStatus()).isEqualTo(413);
			verify(svc, times(1)).decide(any(), any(), anyLong());
		}
		finally {
			controller.close();
		}
	}

}
