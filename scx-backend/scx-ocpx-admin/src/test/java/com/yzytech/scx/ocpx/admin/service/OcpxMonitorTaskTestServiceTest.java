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
	}

	@Test
	void generatesConsistentAndroidParametersWithoutUnresolvedMacros() {
		MonitorTaskTestViews.Preview preview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));

		assertThat(preview.isExecutable()).isTrue();
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
			assertThat(parameter.getValue()).contains("/ocpx/test-callback?request_id=" + preview.getRequestId());
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
		assertThat(mismatch.getBlockers()).anyMatch(message -> message.contains("Android 任务"));

		stubContext(4, "request_id=__REQUEST_ID__&oaid=__OAID__", "ip=__IP__");
		MonitorTaskTestViews.Preview quickApp = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", List.of("oaid")));
		assertThat(quickApp.isExecutable()).isFalse();
		assertThat(quickApp.getBlockers()).anyMatch(message -> message.contains("快应用"));
		assertThat(quickApp.getBlockers()).anyMatch(message -> message.contains("上游模板"));
	}

	@Test
	void recognizesRenrenVideoAliasesAndPrechecksStrictAndroidRequirements() {
		stubRenrenVideoContext();

		MonitorTaskTestViews.Preview defaultPreview = service.generate("task-1",
				new MonitorTaskTestRequests.Generate("ANDROID", null));
		assertThat(defaultPreview.isExecutable()).isTrue();
		assertThat(defaultPreview.getCallbackMode()).isEqualTo("FORMAL_UPSTREAM");
		assertThat(defaultPreview.getCallbackDisplayUrl()).contains("/ocpx/renren-video/callback?kid=");
		assertThat(defaultPreview.getTestUrl()).doesNotContain("callback_url=", "/test-callback");
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
	void reportsCompleteLoopOnlyAfterTheControlledCollectorReceivesCallback() {
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
		MonitorTaskTestViews.Flow downstream = new MonitorTaskTestViews.Flow("SUCCESS", null, "TEST_CALLBACK_RECEIVED",
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
		when(jdbcTemplate.query(contains("message = 'TEST_CALLBACK_RECEIVED'"), any(RowMapper.class), eq("task-1"),
				eq("adv-1"), eq(requestId)))
			.thenReturn(List.of(downstream));

		MonitorTaskTestViews.Result result = service.result("task-1", requestId);

		assertThat(result.getState()).isEqualTo("COMPLETED");
		assertThat(result.getConclusion()).contains("测试回调接收器已收到").contains("不代表真实媒体");
		assertThat(result.getTimeline()).extracting(MonitorTaskTestViews.TimelineItem::getStage)
			.containsExactly("ENGINE_INGRESS", "BILLING", "UPSTREAM", "ADVERTISER_CALLBACK", "CALLBACK_BILLING",
					"MEDIA_DEDUCTION", "DOWNSTREAM_CALLBACK");
		verify(jdbcTemplate).query(contains("AND message NOT IN ('TEST_CALLBACK_ARMED','TEST_CALLBACK_RECEIVED')"),
				any(RowMapper.class), eq("task-1"), eq("adv-1"), eq(requestId));
		verify(jdbcTemplate).query(contains("AND message = 'TEST_CALLBACK_RECEIVED'"), any(RowMapper.class),
				eq("task-1"), eq("adv-1"), eq(requestId));
	}

	@Test
	@SuppressWarnings("unchecked")
	void reportsRenrenFormalCallbackCompleteWithoutInjectingTheTestCollector() {
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
		when(jdbcTemplate.query(contains("message = 'TEST_CALLBACK_RECEIVED'"), any(RowMapper.class), eq("task-1"),
				eq("adv-1"), eq(requestId)))
			.thenReturn(List.of());

		MonitorTaskTestViews.Result result = service.result("task-1", requestId);

		assertThat(result.getState()).isEqualTo("COMPLETED");
		assertThat(result.getConclusion()).contains("人人视频正式 Callback 已成功处理").contains("未注入测试 callback_url");
		assertThat(result.getTimeline()).last().satisfies(item -> {
			assertThat(item.getStage()).isEqualTo("DOWNSTREAM_CALLBACK");
			assertThat(item.getStatus()).isEqualTo("SKIPPED");
			assertThat(item.getDetail()).contains("未配置下游媒体 callback_url");
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
		advertiser.setBidUrl("https://partner.example/click");
		advertiser.setMacroParams(advertiserMacros);
		when(advertiserMapper.selectById("adv-1")).thenReturn(advertiser);

		OcpxPromotionLink link = new OcpxPromotionLink();
		link.setId("link-1");
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
