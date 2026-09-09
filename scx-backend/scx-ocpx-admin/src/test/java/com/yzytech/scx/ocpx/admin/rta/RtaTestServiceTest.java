package com.yzytech.scx.ocpx.admin.rta;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.mapper.RtaManagementSqlMapper;
import com.yzytech.scx.ocpx.admin.service.AdvertiserScope;
import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RtaTestServiceTest {

	static final String ID = "SCX-RTA-" + "a".repeat(32);
	static final String SECRET = "local-test-signing-secret-01234567890123456";

	RtaManagementSqlMapper mapper;

	AdvertiserScope scope;

	RtaTestHttpClient http;

	RtaTestService service;

	Map<String, String> data;

	Map<String, Object> route;

	RtaTestProperties properties;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setup() {
		mapper = mock(RtaManagementSqlMapper.class);
		scope = mock(AdvertiserScope.class);
		http = mock(RtaTestHttpClient.class);
		var redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		data = new HashMap<>();
		when(ops.get(anyString())).thenAnswer(i -> data.get(i.getArgument(0)));
		when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
			.thenAnswer(i -> data.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
		doAnswer(i -> {
			data.put(i.getArgument(0), i.getArgument(1));
			return null;
		}).when(ops).set(anyString(), anyString(), any(Duration.class));
		properties = new RtaTestProperties();
		properties.setCredentials(Map.of("oppo", SECRET));
		var tracking = new OcpxTrackingProperties();
		tracking.setEngineBaseUrl("http://127.0.0.1:10000/ocpx");
		service = new RtaTestService(mapper, scope, properties, tracking, redis, http);
		route = new HashMap<>(Map.of("media_id", "m", "task_name", "Task A", "strategy_id", 452, "credential_ref",
				"oppo", "media_protocol", "OPPO", "upstream_protocol", "WEIFENG_RTA", "upstream_config",
				"{\"aid\":\"5103\",\"did\":\"zsyd\"}", "link_url", "https://ad.adapi.cn/click?aid=5103&did=zsyd"));
		for (String field : List.of("rta_enabled", "binding_enabled", "media_enabled", "upstream_enabled"))
			route.put(field, 1);
		when(mapper.selectRtaTestRoute("A")).thenReturn(route);
		when(http.post(any(), anyString())).thenReturn(new RtaTestHttpClient.Response(200,
				"{\"ret\":0,\"data\":{\"id\":\"" + ID + "\",\"agree_type_list\":[452]}}", null, 20));
	}

	@Test
	void administratorCheckPrecedesAnyObjectLookupOrSend() {
		doThrow(new BootBusinessException("forbidden")).when(scope).requireSystemAdministrator();
		assertThatThrownBy(() -> service.preview("A")).hasMessage("forbidden");
		assertThatThrownBy(() -> service.execute("A", new RtaTestService.Input(ID, "device"))).hasMessage("forbidden");
		assertThatThrownBy(() -> service.result("A", ID)).hasMessage("forbidden");
		verifyNoInteractions(mapper, http);
	}

	@Test
	void realWireUsesConfiguredDestinationAndKeepsSecretsOutOfStoredAndReturnedData() {
		var preview = service.preview("A");
		assertThat(preview.blockers()).isEmpty();
		assertThat(preview.endpoint()).isEqualTo("http://127.0.0.1:10000/ocpx/rta/oppo/oppo");
		var result = service.execute("A", new RtaTestService.Input(ID, "raw-device"));
		assertThat(result).containsEntry("agreed", true).containsEntry("submission", "RECEIVED");
		var capture = ArgumentCaptor.forClass(String.class);
		verify(http).post(eq(java.net.URI.create(preview.endpoint())), capture.capture());
		var body = JSON.parseObject(capture.getValue());
		assertThat(body.getString("oaid")).isEqualTo("raw-device");
		assertThat(body.getJSONArray("ad_type_list")).containsExactly(452);
		assertThat(body.getString("sign")).matches("[a-f0-9]{32}");
		assertThat(body).doesNotContainKey("owner_id");
		assertThat(JSON.toJSONString(preview) + JSON.toJSONString(result) + data.values()).doesNotContain(SECRET,
				"raw-device", body.getString("sign"));
	}

	@Test
	void retryOfUnknownSendNeverResubmitsAndChangedPayloadConflicts() {
		when(http.post(any(), anyString())).thenReturn(new RtaTestHttpClient.Response(null, null, "TIMEOUT", 8000));
		var input = new RtaTestService.Input(ID, "device-a");
		assertThat(service.execute("A", input)).containsEntry("submission", "UNKNOWN");
		service.execute("A", input);
		verify(http, times(1)).post(any(), anyString());
		assertThatThrownBy(() -> service.execute("A", new RtaTestService.Input(ID, "device-b")))
			.hasMessageContaining("不能修改");
		assertThatThrownBy(() -> service.result("B", ID)).hasMessageContaining("不存在");
	}

	@Test
	void disabledRouteOrMissingCredentialNeverSends() {
		route.put("binding_enabled", 0);
		assertThat(service.preview("A").blockers()).isNotEmpty();
		assertThatThrownBy(() -> service.execute("A", new RtaTestService.Input(ID, "device")))
			.hasMessageContaining("均须启用");
		route.put("binding_enabled", 1);
		properties.setCredentials(Map.of());
		assertThatThrownBy(() -> service.execute("A", new RtaTestService.Input(ID, "device")))
			.hasMessageContaining("签名密钥");
		verifyNoInteractions(http);
	}

	@Test
	void unexpectedStrategyAndPartnerEchoAreNotReturnedAsSuccess() {
		when(http.post(any(), anyString())).thenReturn(new RtaTestHttpClient.Response(200,
				"{\"ret\":0,\"msg\":\"secret-echo\",\"data\":{\"id\":\"" + ID + "\",\"agree_type_list\":[999]}}", null,
				2));
		var result = service.execute("A", new RtaTestService.Input(ID, "device"));
		assertThat(result).containsEntry("submission", "UNKNOWN").doesNotContainKey("agreed");
		assertThat(JSON.toJSONString(result) + data.values()).doesNotContain("secret-echo");
	}

	@Test
	void ownerModeAndRedisStateUseOnlyWhitelistedResultFields() {
		route.put("media_config", "{\"ownerMode\":\"VALUE\",\"ownerId\":\"123\"}");
		assertThat(service.signedBody(route, new RtaTestService.Input(ID, "device"), 1788912000000L))
			.containsEntry("owner_id", 123L);
		service.execute("A", new RtaTestService.Input(ID, "device"));
		String internal = "oppo." + sha(ID + ":452");
		data.put(RtaContract.PREFIX + "idem:m:" + internal, "relay");
		data.put(RtaContract.PREFIX + "state:relay",
				JSON.toJSONString(Map.of("requestId", internal, "mediaId", "m", "bindings",
						List.of(Map.of("taskId", "A")), "relayRequestId", "relay", "status", "ALLOW",
						"encryptedPayload", "secret-envelope", "media", Map.of("secret", "never-return"))));
		var result = service.result("A", ID);
		assertThat(result).containsEntry("status", "ALLOW").containsEntry("relayRequestId", "relay");
		assertThat(JSON.toJSONString(result)).doesNotContain("secret-envelope", "never-return", "fingerprint", "oaid");
	}

	private static String sha(String value) {
		try {
			return HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
