package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskTestSqlMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskTestRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskTestViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.protocol.OcpxUpstreamProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxMonitorTaskTestServiceTest {

	private OcpxMonitorTaskMapper taskMapper;

	private OcpxAdvertiserMapper advertiserMapper;

	private OcpxPromotionLinkMapper linkMapper;

	private OcpxMediaMapper mediaMapper;

	private AdvertiserScope advertiserScope;

	private JdbcTemplate jdbcTemplate;

	private OcpxMonitorTaskTestHttpClient httpClient;

	private OcpxMonitorTaskTestService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		taskMapper = mock(OcpxMonitorTaskMapper.class);
		advertiserMapper = mock(OcpxAdvertiserMapper.class);
		linkMapper = mock(OcpxPromotionLinkMapper.class);
		mediaMapper = mock(OcpxMediaMapper.class);
		advertiserScope = mock(AdvertiserScope.class);
		jdbcTemplate = mock(JdbcTemplate.class);
		httpClient = mock(OcpxMonitorTaskTestHttpClient.class);
		OcpxTrackingProperties tracking = new OcpxTrackingProperties();
		tracking.setEngineBaseUrl("http://127.0.0.1:10000/ocpx");
		service = new OcpxMonitorTaskTestService(taskMapper, advertiserMapper, linkMapper, mediaMapper, tracking,
				advertiserScope, XmlMapperTestSupport.mapper(OcpxMonitorTaskTestSqlMapper.class, jdbcTemplate),
				httpClient);
		stubContext(1,
				"request_id=__REQUEST_ID__&oaid=__OAID__&oaid_md5=__OAID_MD5__&androidid=__ANDROIDID__"
						+ "&ip=__IP__&ua=__UA__&os=__OS__&model=__MODEL__&ts=__TS__&clickid=__CLICKID__",
				"oaid=__OAID__&oaid_md5=__OAID_MD5__&android_id=__ANDROIDID__&ip=__IP__&ua=__UA__"
						+ "&os=__OS__&model=__MODEL__&clickid=__CLICKID__");
		when(jdbcTemplate.query(contains("available_balance"), any(RowMapper.class), eq("adv-1")))
			.thenReturn(List.of(new BigDecimal("100.000000")));
		when(jdbcTemplate.query(contains("callback_url"), any(RowMapper.class),
				any(Object[].class)))
			.thenReturn(List.of(new OcpxMonitorTaskTestSqlMapper.SelectCallbackFeedbackRow(101L,
					"https://8.8.8.8/real-media-callback?clickid=previous", "adv-1", "link-1", "media-1", "task-1")));
	}

	@Test
	void generatesConsistentAndroidParametersWithoutUnresolvedMacros() {
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));

		assertThat(preview.isExecutable()).isTrue();
		assertThat(preview.getMediaCallbackMode()).isEqualTo("REAL_MEDIA");
		assertThat(preview.getRequestId()).matches("SCX-UAT-\\d{14}-[0-9a-f]{12}");
		assertThat(preview.getTestUrl())
			.contains("request_id=" + preview.getRequestId(), "clickid=" + preview.getRequestId(), "os=0", "event=CLK",
					"mId=media-1", "tId=task-1", "aId=adv-1", "lId=link-1", "callback_url=")
			.doesNotContain("__");
		assertThat(preview.getCallbackToken()).matches("[0-9a-f]{32}");
		assertThat(preview.getParameters()).anySatisfy(parameter -> {
			assertThat(parameter.getKey()).isEqualTo("callback_url");
			assertThat(parameter.isRequired()).isTrue();
			assertThat(parameter.isEditable()).isFalse();
			assertThat(parameter.getValue()).isEqualTo("https://8.8.8.8/real-media-callback?clickid=previous");
		});
		Map<String, String> values = preview.getParameters()
			.stream()
			.filter(MonitorTaskTestViews.Parameter::isSelected)
			.collect(java.util.stream.Collectors.toMap(MonitorTaskTestViews.Parameter::getKey,
					MonitorTaskTestViews.Parameter::getValue));
		assertThat(values.get("oaid")).matches("[0-9a-f]{64}");
		assertThat(values.get("androidid")).matches("[0-9a-f]{16}");
		assertThat(values.get("oaid_md5")).matches("[0-9a-f]{32}");
		assertThat(values.get("oaid_md5")).isEqualTo(md5(values.get("oaid")));
		assertThat(preview.getExpectedCharge()).isEqualByComparingTo("5");
		verify(advertiserScope).requireSystemAdministrator();
	}

	@Test
	void blocksCompleteTestWhenNoRealMediaCallbackSnapshotExists() {
		when(jdbcTemplate.query(contains("callback_url"), any(RowMapper.class), any(Object[].class)))
			.thenReturn(List.of());

		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));

		assertThat(preview.isExecutable()).isFalse();
		assertThat(preview.getTestUrl()).isEmpty();
		assertThat(preview.getBlockers()).anyMatch(message -> message.contains("没有真实媒体 callback_url"));
		verify(httpClient, never()).get(any());
	}

	@Test
	void simulatedMediaModeUsesFixedEngineCallbackWithoutRealMediaSnapshot() {
		when(jdbcTemplate.query(contains("callback_url"), any(RowMapper.class), any(Object[].class)))
			.thenReturn(List.of());

		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null, "SIMULATED_MEDIA"));

		assertThat(preview.isExecutable()).isTrue();
		assertThat(preview.getMediaCallbackMode()).isEqualTo("SIMULATED_MEDIA");
		assertThat(preview.getCallbackFeedbackLogId()).isNull();
		assertThat(preview.getCallbackDisplayUrl())
			.startsWith("http://127.0.0.1:10000/ocpx/test-callback?")
			.contains("request_id=" + preview.getRequestId(), "token=" + preview.getCallbackToken(),
					"mId=media-1", "tId=task-1", "aId=adv-1", "lId=link-1");
		assertThat(preview.getWarnings())
			.anyMatch(message -> message.contains("模拟媒体模式") && message.contains("仅模拟最后一跳"));
		assertThat(preview.getParameters()).anySatisfy(parameter -> {
			assertThat(parameter.getKey()).isEqualTo("callback_url");
			assertThat(parameter.getLabel()).isEqualTo("模拟媒体 Callback（仅最后一跳）");
			assertThat(parameter.getValue()).startsWith("http://127.0.0.1:10000/ocpx/test-callback?");
		});
	}

	@Test
	void simulatedMediaModeExecutesWithFixedEngineCallbackWithoutSnapshot() {
		when(jdbcTemplate.query(contains("callback_url"), any(RowMapper.class), any(Object[].class)))
			.thenReturn(List.of());
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid"), "SIMULATED_MEDIA"));
		when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), eq("task-1"),
				eq(preview.getRequestId())))
			.thenReturn(0L);
		when(httpClient.get(any())).thenReturn(new OcpxMonitorTaskTestHttpClient.Response(200, "{}", null, 1L));

		MonitorTaskTestViews.Execution execution = service.execute("task-1",
				new MonitorTaskTestRequests.Execute(preview.getRequestId(), "ANDROID", preview.getCallbackToken(),
						"SIMULATED_MEDIA", editableValues(preview)));

		assertThat(execution.getTestUrl()).isEqualTo(preview.getTestUrl());
		assertThat(execution.getTestUrl()).contains("test-callback");
		verify(httpClient).get(any());
	}

	@Test
	void simulatedMediaModeRejectsFeedbackSnapshotSelection() {
		assertThatThrownBy(() -> service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null, null, 101L, "SIMULATED_MEDIA")))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不能指定 feedbackLogId");

		assertThatThrownBy(() -> service.execute("task-1",
				new MonitorTaskTestRequests.Execute("SCX-UAT-20260817183000-abcdef123456", "ANDROID",
						"0123456789abcdef0123456789abcdef", 101L, "SIMULATED_MEDIA",
						Map.of("oaid", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不能指定 feedbackLogId");
		verify(httpClient, never()).get(any());
	}

	@Test
	void rejectsPermissionCodeHolderWhoIsNotARealSystemAdministrator() {
		doThrow(new BootBusinessException("仅系统管理员可执行此操作")).when(advertiserScope).requireSystemAdministrator();

		assertThatThrownBy(
				() -> service.generate("task-1", new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("仅系统管理员");
		verify(taskMapper, never()).selectById(anyString());
	}

	@Test
	void blocksOsMismatchQuickAppAndMissingForwardableDevice() {
		MonitorTaskTestViews.Preview mismatch = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("IOS", List.of("idfa")));
		assertThat(mismatch.isExecutable()).isFalse();
		assertThat(mismatch.getTestUrl()).isEmpty();
		assertThat(mismatch.getBlockers()).anyMatch(message -> message.contains("Android 任务"));

		stubContext(4, "request_id=__REQUEST_ID__&oaid=__OAID__", "ip=__IP__");
		MonitorTaskTestViews.Preview quickApp = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid")));
		assertThat(quickApp.isExecutable()).isFalse();
		assertThat(quickApp.getTestUrl()).isEmpty();
		assertThat(quickApp.getBlockers()).anyMatch(message -> message.contains("快应用"));
		assertThat(quickApp.getBlockers()).anyMatch(message -> message.contains("上游模板"));
	}

	@ParameterizedTest
	@EnumSource(value = OcpxUpstreamProtocol.class, names = { "AERIM", "WEIFENG" })
	void linkProtocolsGenerateAndExecuteWithNoAdvertiserTemplate(OcpxUpstreamProtocol protocol) {
		OcpxAdvertiser advertiser = advertiserMapper.selectById("adv-1");
		advertiser.setUpstreamProtocol(protocol);
		advertiser.setBidUrl(null);
		advertiser.setMacroParams(null);
		linkMapper.selectById("link-1")
			.setLinkUrl("https://partner.example/click?aid=1&did=2&oaid=__OAID__"
					+ "&ip=__IP__&ua=__UA__&model=__MODEL__&ts=__TS__&os=__OS__&callback=__CALLBACK__");

		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));

		assertThat(preview.isExecutable()).as("%s blockers: %s", protocol, preview.getBlockers()).isTrue();
		assertThat(preview.getTestUrl()).contains("oaid=", "ip=", "ua=", "model=", "ts=").doesNotContain("__");
		Map<String, String> values = editableValues(preview);
		when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), eq("task-1"),
				eq(preview.getRequestId())))
			.thenReturn(0L);
		when(httpClient.get(any())).thenReturn(new OcpxMonitorTaskTestHttpClient.Response(200, "{}", null, 1L));
		MonitorTaskTestViews.Execution execution = service.execute("task-1", new MonitorTaskTestRequests.Execute(
				preview.getRequestId(), "ANDROID", preview.getCallbackToken(), values));
		assertThat(execution.getTestUrl()).isEqualTo(preview.getTestUrl());
		verify(httpClient).get(any());
	}

	@ParameterizedTest
	@EnumSource(value = OcpxUpstreamProtocol.class, names = { "AERIM", "WEIFENG" })
	void ignoresConflictingAdvertiserFieldsAndNeverFallsBackWhenLinkIsMissing(OcpxUpstreamProtocol protocol) {
		OcpxAdvertiser advertiser = advertiserMapper.selectById("adv-1");
		advertiser.setUpstreamProtocol(protocol);
		advertiser.setBidUrl("https://old.example/click?imei=__IMEI__");
		advertiser.setMacroParams("imei=__IMEI__&callback=__CALLBACK__");
		OcpxPromotionLink link = linkMapper.selectById("link-1");
		link.setLinkUrl("https://partner.example/click?oaid=__OAID__&model=__MODEL__&ts=__TS__&callback=__CALLBACK__");
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));
		assertThat(preview.isExecutable()).isTrue();
		assertThat(preview.getTestUrl()).contains("oaid=").doesNotContain("imei=");

		link.setLinkUrl(null);
		MonitorTaskTestViews.Preview missing = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("imei")));
		assertThat(missing.isExecutable()).isFalse();
		assertThat(missing.getTestUrl()).isEmpty();
		assertThat(missing.getBlockers()).anyMatch(value -> value.contains("推广链接"));
		verify(httpClient, never()).get(any());
	}

	@Test
	void recognizesAerimAliasesWithoutRequiringOldAdvertiserMacros() {
		advertiserMapper.selectById("adv-1").setUpstreamProtocol(OcpxUpstreamProtocol.AERIM);
		linkMapper.selectById("link-1")
			.setLinkUrl("https://partner.example/click?oaid_md5=__OAIDMD5__"
					+ "&ip=__IPV4__&ua=__UA__&ts=__TSMS__&callback=__CALLBACK__&clickid=__CLICK_ID__&os=__OS_TYPE__");
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid_md5", "ip", "ua", "mts")));
		assertThat(preview.isExecutable()).isTrue();
		assertThat(preview.getTestUrl()).contains("oaid_md5=", "mts=", "ip=", "ua=");
		assertThat(preview.getParameters()).filteredOn(MonitorTaskTestViews.Parameter::isSelected)
			.allSatisfy(parameter -> {
				if (!parameter.getKey().equals("request_id"))
					assertThat(parameter.isForwardedUpstream()).isTrue();
			});
	}

	@Test
	void aerimIosHashOnlyUsesLinkTemplateAndPreservesEditedEncoding() {
		advertiserMapper.selectById("adv-1").setUpstreamProtocol(OcpxUpstreamProtocol.AERIM);
		advertiserMapper.selectById("adv-1").setBidUrl(null);
		advertiserMapper.selectById("adv-1").setMacroParams(null);
		OcpxPromotionLink link = linkMapper.selectById("link-1");
		link.setPlatform(2);
		link.setLinkUrl("https://partner.example/click?idfa_md5=__IDFA_MD5__&ua=__UA__&callback=__CALLBACK__");
		Map<String, String> values = Map.of("idfa_md5", "0123456789abcdef0123456789abcdef", "ua",
				"Test UA/1.0 (iPhone; x=1&y=2+3)");
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("IOS", List.of("idfa_md5", "ua"), values));
		assertThat(preview.isExecutable()).isTrue();
		assertThat(preview.getTestUrl()).contains("idfa_md5=", "%26y%3D2%2B3").doesNotContain("ip=");
		when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), eq("task-1"),
				eq(preview.getRequestId())))
			.thenReturn(0L);
		when(httpClient.get(any())).thenReturn(new OcpxMonitorTaskTestHttpClient.Response(200, "{}", null, 1L));
		assertThat(service
			.execute("task-1",
					new MonitorTaskTestRequests.Execute(preview.getRequestId(), "IOS", preview.getCallbackToken(),
							values))
			.getTestUrl()).isEqualTo(preview.getTestUrl());
		MonitorTaskTestViews.Preview hashOnly = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("IOS", List.of("idfa_md5")));
		assertThat(hashOnly.isExecutable()).isTrue();
		assertThat(hashOnly.getTestUrl()).doesNotContain("ua=", "ip=");
	}

	@Test
	void gravityUsesDynamicDeviceCapabilitiesFromBaseLink() {
		advertiserMapper.selectById("adv-1").setUpstreamProtocol(OcpxUpstreamProtocol.GRAVITY);
		OcpxPromotionLink link = linkMapper.selectById("link-1");
		link.setPlatform(3);
		link.setLinkUrl("https://partner.example/click?campaign=business-id");
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));
		assertThat(preview.isExecutable()).isTrue();
		assertThat(preview.getTestUrl()).contains("oaid=", "ip=", "ua=");
		MonitorTaskTestViews.Preview missing = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid")));
		assertThat(missing.getBlockers()).anyMatch(value -> value.contains("ip"))
			.anyMatch(value -> value.contains("ua"));
		assertThat(missing.getTestUrl()).isEmpty();
		link.setPlatform(1);
		MonitorTaskTestViews.Preview wrongPlatform = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));
		assertThat(wrongPlatform.isExecutable()).isFalse();
		assertThat(wrongPlatform.getBlockers()).contains("引力推广链接必须配置为 Android+IOS 平台");
	}

	@Test
	void templateGetUsesEffectiveQueryInsteadOfConcatenatedMacroNames() {
		OcpxAdvertiser advertiser = advertiserMapper.selectById("adv-1");
		advertiser.setBidUrl("https://partner.example/click?oaid=fixed-value");
		advertiser.setMacroParams("oaid=__OAID__");
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid")));
		assertThat(preview.isExecutable()).isFalse();
		assertThat(preview.getTestUrl()).isEmpty();
		advertiser.setMacroParams("__OAID__,__IP__,__UA__");
		assertThat(service.generate("task-1", new MonitorTaskTestRequests.Generate("ANDROID", null)).isExecutable())
			.isFalse();
	}

	@Test
	void cannotGenerateExecutableLinksWithNoProtocolOrNoSelectedIdentity() {
		MonitorTaskTestViews.Preview empty = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of()));
		assertThat(empty.isExecutable()).isFalse();
		assertThat(empty.getTestUrl()).isEmpty();
		advertiserMapper.selectById("adv-1").setUpstreamProtocol(null);
		MonitorTaskTestViews.Preview unconfigured = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));
		assertThat(unconfigured.getBlockers()).contains("广告主尚未选择上游协议");
		assertThat(unconfigured.getTestUrl()).isEmpty();
		assertThatThrownBy(() -> service.execute("task-1", new MonitorTaskTestRequests.Execute(empty.getRequestId(),
				"ANDROID", empty.getCallbackToken(), Map.of())))
			.isInstanceOf(BootBusinessException.class);
		verify(httpClient, never()).get(any());
	}

	@Test
	void editedParameterPreviewValidatesValuesAndPreservesThemWithoutCallingEngine() {
		Map<String, String> values = Map.of("oaid", "0123456789abcdef0123456789abcdef");
		MonitorTaskTestRequests.Generate request = new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid"),
				values);
		MonitorTaskTestViews.Preview preview = service.generate("task-1", request);
		assertThat(preview.isExecutable()).isTrue();
		assertThat(preview.getTestUrl()).contains("oaid=" + values.get("oaid"));
		assertThat(editableValues(preview)).isEqualTo(values);
		assertThat(service.generate("task-1", request).getRequestId()).isNotEqualTo(preview.getRequestId());
		assertThatThrownBy(() -> service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid"), Map.of("oaid", ""))))
			.isInstanceOf(BootBusinessException.class);
		assertThatThrownBy(() -> service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid", "ip"), values)))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("不一致");
		verify(httpClient, never()).get(any());
	}

	private Map<String, String> editableValues(MonitorTaskTestViews.Preview preview) {
		return preview.getParameters()
			.stream()
			.filter(parameter -> parameter.isSelected() && !parameter.isRequired())
			.collect(java.util.stream.Collectors.toMap(MonitorTaskTestViews.Parameter::getKey,
					MonitorTaskTestViews.Parameter::getValue));
	}

	@Test
	void recognizesRenrenVideoAliasesAndPrechecksStrictAndroidRequirements() {
		stubRenrenVideoContext();

		MonitorTaskTestViews.Preview defaultPreview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));
		assertThat(defaultPreview.isExecutable()).isTrue();
		assertThat(defaultPreview.getCallbackMode()).isEqualTo("FORMAL_UPSTREAM");
		assertThat(defaultPreview.getCallbackDisplayUrl()).isEqualTo("https://8.8.8.8/real-media-callback?clickid=previous");
		assertThat(defaultPreview.getTestUrl()).contains("callback_url=").doesNotContain("/test-callback");
		assertThat(defaultPreview.getParameters()).anySatisfy(parameter -> {
			assertThat(parameter.getKey()).isEqualTo("callback");
			assertThat(parameter.getMacro()).isEqualTo("__CALLBACK__");
			assertThat(parameter.getValue()).contains("/ocpx/renren-video/callback");
		});
		assertThat(defaultPreview.getParameters()).filteredOn(MonitorTaskTestViews.Parameter::isSelected)
			.extracting(MonitorTaskTestViews.Parameter::getKey)
			.contains("imei_md5", "oaid", "oaid_md5", "ip", "ua", "mts");

		MonitorTaskTestViews.Preview incomplete = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid")));
		assertThat(incomplete.isExecutable()).isFalse();
		assertThat(incomplete.getBlockers()).anyMatch(message -> message.contains("imei_md5"))
			.anyMatch(message -> message.contains("oaid_md5"))
			.anyMatch(message -> message.contains("时间戳"));
	}

	@Test
	void rejectsInvalidEditedValuesBeforeCallingEngine() {
		assertThatThrownBy(() -> service.execute("task-1",
				new MonitorTaskTestRequests.Execute("SCX-UAT-20260817183000-abcdef123456", "ANDROID",
						"0123456789abcdef0123456789abcdef", Map.of("oaid", "not-an-oaid"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("OAID 格式无效");
		verify(httpClient, never()).get(any());

		assertThatThrownBy(() -> service.execute("task-1",
				new MonitorTaskTestRequests.Execute("SCX-UAT-20260817183000-abcdef123456", "ANDROID",
						"0123456789abcdef0123456789abcdef",
						Map.of("oaid", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "oaid_md5",
								"00000000000000000000000000000000"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessageContaining("oaid_md5 与原值不一致");
	}

	@Test
	void rejectsUnsafeMediaRouteWithoutExposingTheGeneratedUrl() {
		when(mediaMapper.selectById("media-1")).thenAnswer(invocation -> {
			OcpxMedia media = new OcpxMedia();
			media.setId("media-1");
			media.setMediaName("媒体一");
			media.setUri("https://untrusted.example/feedback");
			media.setMacroParams("request_id=__REQUEST_ID__&oaid=__OAID__");
			return media;
		});

		assertThatThrownBy(
				() -> service.generate("task-1", new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid"))))
			.isInstanceOf(BootBusinessException.class)
			.hasMessage("Engine 测试地址配置无效")
			.hasMessageNotContaining("untrusted")
			.hasMessageNotContaining("SCX-UAT");
		verify(httpClient, never()).get(any());
	}

	@Test
	void executesAgainstFixedEngineAndReturnsSanitizedTransportResult() {
		String requestId = "SCX-UAT-20260817183000-abcdef123456";
		when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), eq("task-1"), eq(requestId)))
			.thenReturn(0L);
		when(httpClient.get(any()))
			.thenReturn(new OcpxMonitorTaskTestHttpClient.Response(200, "{\"code\":200,\"msg\":\"提交成功\"}", null, 18L));

		MonitorTaskTestViews.Execution execution = service.execute("task-1",
				new MonitorTaskTestRequests.Execute(requestId, "ANDROID", "0123456789abcdef0123456789abcdef",
						Map.of("oaid", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));

		assertThat(execution.isAlreadyExecuted()).isFalse();
		assertThat(execution.getEngineHttpStatus()).isEqualTo(200);
		assertThat(execution.getTestUrl()).startsWith("http://127.0.0.1:10000/ocpx/feedback?");
		assertThat(execution.getDurationMs()).isEqualTo(18L);
	}

	@Test
	void repeatedExecutionDoesNotCallEngineAgain() {
		String requestId = "SCX-UAT-20260817183000-abcdef123456";
		when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), eq("task-1"), eq(requestId)))
			.thenReturn(1L);

		MonitorTaskTestViews.Execution execution = service.execute("task-1",
				new MonitorTaskTestRequests.Execute(requestId, "ANDROID", "0123456789abcdef0123456789abcdef",
						Map.of("oaid", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));

		assertThat(execution.isAlreadyExecuted()).isTrue();
		assertThat(execution.getErrorCode()).isEqualTo("ALREADY_EXECUTED");
		verify(httpClient, never()).get(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void reportsFactBillingAndUpstreamHttpWithoutClaimingPartnerPersistence() {
		String requestId = "SCX-UAT-20260817183000-abcdef123456";
		MonitorTaskTestViews.Fact fact = new MonitorTaskTestViews.Fact("CLK", "POSTED", null,
				new BigDecimal("5.000000"), LocalDateTime.now());
		MonitorTaskTestViews.Flow flow = new MonitorTaskTestViews.Flow("SUCCESS", 200, "AERIM_DELIVERED", 22L,
				LocalDateTime.now());
		when(jdbcTemplate.query(contains("flow_type='FEEDBACK'"), any(RowMapper.class), eq("task-1"), eq("adv-1"),
				eq(requestId)))
			.thenReturn(List.of(fact));
		when(jdbcTemplate.query(contains("event_id=? AND flow_type='FEEDBACK'"), any(RowMapper.class), eq("task-1"),
				eq("adv-1"), eq(requestId)))
			.thenReturn(List.of(flow));

		MonitorTaskTestViews.Result result = service.result("task-1", requestId);

		assertThat(result.getState()).isEqualTo("UPSTREAM_RESPONDED");
		assertThat(result.getFact().getBillingStatus()).isEqualTo("POSTED");
		assertThat(result.getFlow().getPartnerHttpStatus()).isEqualTo(200);
		assertThat(result.getConclusion()).contains("不代表合作方已经入库");
		assertThat(result.getTimeline()).extracting(MonitorTaskTestViews.TimelineItem::getStage)
			.containsExactly("ENGINE_INGRESS", "BILLING", "UPSTREAM", "ADVERTISER_CALLBACK");
	}

	@Test
	@SuppressWarnings("unchecked")
	void reportsCompleteLoopOnlyAfterRealMediaReceivesCallback() {
		String requestId = "SCX-UAT-20260817183000-abcdef123456";
		LocalDateTime now = LocalDateTime.now();
		MonitorTaskTestViews.Fact fact = new MonitorTaskTestViews.Fact("CLK", "NOT_APPLICABLE", "BILLING_DISABLED",
				BigDecimal.ZERO, now);
		MonitorTaskTestViews.Flow upstream = new MonitorTaskTestViews.Flow("SUCCESS", 200, "WEIFENG_DELIVERED", 22L,
				now);
		MonitorTaskTestViews.CallbackFact callbackFact = new MonitorTaskTestViews.CallbackFact("ACTIVATE", "POSTED",
				null, new BigDecimal("5.000000"), BigDecimal.ZERO, "PASSED", BigDecimal.ONE, now, now);
		MonitorTaskTestViews.Flow callback = new MonitorTaskTestViews.Flow("SUCCESS", null,
				"DOWNSTREAM_DELIVERY_SUBMITTED", 0L, now);
		MonitorTaskTestViews.Flow downstream = new MonitorTaskTestViews.Flow("SUCCESS", null, "MEDIA_DELIVERED",
				0L, now);
		when(jdbcTemplate.query(contains("flow_type='FEEDBACK'"), any(RowMapper.class), eq("task-1"), eq("adv-1"),
				eq(requestId)))
			.thenReturn(List.of(fact));
		when(jdbcTemplate.query(contains("event_id=? AND flow_type='FEEDBACK'"), any(RowMapper.class), eq("task-1"),
				eq("adv-1"), eq(requestId)))
			.thenReturn(List.of(upstream));
		when(jdbcTemplate.query(contains("flow_type='CALLBACK'"), any(RowMapper.class), eq("task-1"), eq("adv-1"),
				eq(requestId)))
			.thenReturn(List.of(callbackFact));
		when(jdbcTemplate.query(contains("message NOT IN ('TEST_CALLBACK_ARMED','TEST_CALLBACK_RECEIVED')"),
				any(RowMapper.class), eq("task-1"), eq("adv-1"), eq(requestId)))
			.thenReturn(List.of(callback));
		when(jdbcTemplate.query(contains("message LIKE 'MEDIA_%'"), any(RowMapper.class), eq("task-1"),
				eq("adv-1"), eq(requestId)))
			.thenReturn(List.of(downstream));

		MonitorTaskTestViews.Result result = service.result("task-1", requestId);

		assertThat(result.getState()).isEqualTo("COMPLETED");
		assertThat(result.getConclusion()).contains("真实媒体 callback_url 已收到");
		assertThat(result.getTimeline()).extracting(MonitorTaskTestViews.TimelineItem::getStage)
			.containsExactly("ENGINE_INGRESS", "BILLING", "UPSTREAM", "ADVERTISER_CALLBACK", "CALLBACK_BILLING",
					"MEDIA_DEDUCTION", "DOWNSTREAM_CALLBACK");
		verify(jdbcTemplate).query(contains("AND message NOT IN ('TEST_CALLBACK_ARMED','TEST_CALLBACK_RECEIVED')"),
				any(RowMapper.class), eq("task-1"), eq("adv-1"), eq(requestId));
		verify(jdbcTemplate).query(contains("AND message LIKE 'MEDIA_%'"), any(RowMapper.class),
				eq("task-1"), eq("adv-1"), eq(requestId));
	}

	@Test
	@SuppressWarnings("unchecked")
	void reportsRenrenFormalCallbackCompleteWithRealMediaCallback() {
		stubRenrenVideoContext();
		String requestId = "SCX-UAT-20260817183000-abcdef123456";
		LocalDateTime now = LocalDateTime.now();
		MonitorTaskTestViews.Fact fact = new MonitorTaskTestViews.Fact("CLK", "NOT_APPLICABLE", "BILLING_DISABLED",
				BigDecimal.ZERO, now);
		MonitorTaskTestViews.Flow upstream = new MonitorTaskTestViews.Flow("SUCCESS", 200, "RENREN_VIDEO_DELIVERED",
				22L, now);
		MonitorTaskTestViews.CallbackFact callbackFact = new MonitorTaskTestViews.CallbackFact("ACTIVATE",
				"NOT_APPLICABLE", "BILLING_DISABLED", BigDecimal.ZERO, BigDecimal.ZERO, "PASSED", BigDecimal.ONE, now,
				now);
		MonitorTaskTestViews.Flow callback = new MonitorTaskTestViews.Flow("SUCCESS", 200,
				"DOWNSTREAM_CALLBACK_MISSING", 0L, now);
		when(jdbcTemplate.query(contains("flow_type='FEEDBACK'"), any(RowMapper.class), eq("task-1"), eq("adv-1"),
				eq(requestId)))
			.thenReturn(List.of(fact));
		when(jdbcTemplate.query(contains("event_id=? AND flow_type='FEEDBACK'"), any(RowMapper.class), eq("task-1"),
				eq("adv-1"), eq(requestId)))
			.thenReturn(List.of(upstream));
		when(jdbcTemplate.query(contains("flow_type='CALLBACK'"), any(RowMapper.class), eq("task-1"), eq("adv-1"),
				eq(requestId)))
			.thenReturn(List.of(callbackFact));
		when(jdbcTemplate.query(contains("message NOT IN ('TEST_CALLBACK_ARMED','TEST_CALLBACK_RECEIVED')"),
				any(RowMapper.class), eq("task-1"), eq("adv-1"), eq(requestId)))
			.thenReturn(List.of(callback));
		when(jdbcTemplate.query(contains("message LIKE 'MEDIA_%'"), any(RowMapper.class), eq("task-1"),
				eq("adv-1"), eq(requestId)))
			.thenReturn(List.of());

		MonitorTaskTestViews.Result result = service.result("task-1", requestId);

		assertThat(result.getState()).isEqualTo("DOWNSTREAM_PENDING");
		assertThat(result.getConclusion()).contains("等待真实媒体确认");
		assertThat(result.getTimeline()).last().satisfies(item -> {
			assertThat(item.getStage()).isEqualTo("DOWNSTREAM_CALLBACK");
			assertThat(item.getStatus()).isEqualTo("WAITING");
			assertThat(item.getDetail()).contains("等待真实媒体");
		});
	}

	private void stubContext(int platform, String mediaMacros, String advertiserMacros) {
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setId("task-1");
		task.setMonitorName("测试任务");
		task.setAdvertiserId("adv-1");
		task.setPromotionLinkId("link-1");
		task.setMediaId("media-1");
		task.setBillingEnabled(true);
		task.setBillingType(1);
		task.setUnitPrice(new BigDecimal("5.000000"));
		when(taskMapper.selectById("task-1")).thenReturn(task);

		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("adv-1");
		advertiser.setAdvertiserName("广告主一");
		advertiser.setUpstreamProtocol(OcpxUpstreamProtocol.TEMPLATE_GET);
		advertiser.setBidUrl("https://partner.example/click");
		advertiser.setMacroParams(advertiserMacros);
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser);

		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-1");
		link.setAdvertiserId("adv-1");
		link.setLinkName("链接一");
		link.setPlatform(platform);
		when(linkMapper.selectById("link-1")).thenReturn(link);

		OcpxMedia media = new OcpxMedia();
		media.setId("media-1");
		media.setMediaName("媒体一");
		media.setUri("/feedback");
		media.setMacroParams(mediaMacros);
		when(mediaMapper.selectById("media-1")).thenReturn(media);
	}

	private void stubRenrenVideoContext() {
		String mediaMacros = "request_id=__REQUEST_ID__&imei_md5=__IMEI_MD5__&oaid=__OAID__"
				+ "&oaid_md5=__OAID_MD5__&ip=__IP__&ua=__UA__&os=__OS__&mts=__MTS__&clickid=__CLICKID__";
		stubContext(1, mediaMacros, null);

		OcpxAdvertiser advertiser = new OcpxAdvertiser();
		advertiser.setId("adv-1");
		advertiser.setAdvertiserName("人人视频广告主");
		advertiser.setUpstreamProtocol(OcpxUpstreamProtocol.RENREN_VIDEO);
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser);

		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-1");
		link.setAdvertiserId("adv-1");
		link.setLinkName("人人视频 Android");
		link.setPlatform(1);
		link.setLinkUrl("https://monitor.partner.example/ad-action/click?userId=business-user"
				+ "&imeiMd5=__IMEI__&oaid=__OAID__&oaidMd5=__OAID_MD5__&ip=__IP__&os=__OS__"
				+ "&ua=__UA__&ts=__TS__&callback=__CALLBACK__&requestId=__REQID__&clickId=__CLICK_ID__");
		when(linkMapper.selectById("link-1")).thenReturn(link);
	}

	private String md5(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

}
