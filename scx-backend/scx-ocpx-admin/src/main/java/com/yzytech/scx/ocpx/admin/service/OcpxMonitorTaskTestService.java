package com.yzytech.scx.ocpx.admin.service;

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
import com.yzytech.scx.ocpx.protocol.RenrenVideoTemplateContract;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OcpxMonitorTaskTestService {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private static final DateTimeFormatter TEST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private static final Pattern TEST_ID = Pattern.compile("SCX-UAT-\\d{14}-[0-9a-f]{12}");

	private static final Pattern CALLBACK_TOKEN = Pattern.compile("[0-9a-f]{32}");

	private static final Pattern MD5 = Pattern.compile("[0-9a-fA-F]{32}");

	private static final Pattern HEX_16 = Pattern.compile("[0-9a-fA-F]{16}");

	private static final Pattern OAID = Pattern.compile("[0-9a-f]{16,128}");

	private static final Pattern UUID_VALUE = Pattern
		.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private static final Pattern IDFA = Pattern.compile("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}");

	private static final Map<String, String> MD5_ORIGINALS = Map.of("imei_md5", "imei", "oaid_md5", "oaid",
			"androidid_md5", "androidid", "idfa_md5", "idfa", "mac_md5", "mac");

	private static final Pattern MAC = Pattern.compile("[0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5}");

	private static final List<ParameterSpec> PARAMETER_SPECS = List
		.of(new ParameterSpec("caid", "CAID", "设备标识", "__CAID__", OsSupport.IOS, true),
				new ParameterSpec("caid_list", "CAID 列表", "设备标识", "__CAID_LIST__", OsSupport.IOS, true),
				new ParameterSpec("caid1", "CAID1", "设备标识", "__CAID1__", OsSupport.IOS, true),
				new ParameterSpec("caid2", "CAID2", "设备标识", "__CAID2__", OsSupport.IOS, true),
				new ParameterSpec("last_caid", "历史 CAID", "设备标识", "__LAST_CAID__", OsSupport.IOS, true),
				new ParameterSpec("prev_caid", "上一 CAID", "设备标识", "__PREV_CAID__", OsSupport.IOS, true),
				new ParameterSpec("imei", "IMEI", "设备标识", "__IMEI__", OsSupport.ANDROID, true),
				new ParameterSpec("imei_md5", "IMEI MD5", "设备标识", "__IMEI_MD5__", OsSupport.ANDROID, true),
				new ParameterSpec("oaid", "OAID", "设备标识", "__OAID__", OsSupport.ANDROID, true),
				new ParameterSpec("oaid_md5", "OAID MD5", "设备标识", "__OAID_MD5__", OsSupport.ANDROID, true),
				new ParameterSpec("androidid", "Android ID", "设备标识", "__ANDROIDID__", OsSupport.ANDROID, true),
				new ParameterSpec("androidid_md5", "Android ID MD5", "设备标识", "__ANDROIDID_MD5__", OsSupport.ANDROID,
						true),
				new ParameterSpec("aaid", "AAID", "设备标识", "__AAID__", OsSupport.ANDROID, true),
				new ParameterSpec("idfa", "IDFA", "设备标识", "__IDFA__", OsSupport.IOS, true),
				new ParameterSpec("idfa_md5", "IDFA MD5", "设备标识", "__IDFA_MD5__", OsSupport.IOS, true),
				new ParameterSpec("mac", "MAC", "设备标识", "__MAC__", OsSupport.ANDROID, false),
				new ParameterSpec("mac_md5", "MAC MD5", "设备标识", "__MAC_MD5__", OsSupport.BOTH, false),
				new ParameterSpec("ip", "IP", "网络终端", "__IP__", OsSupport.BOTH, false),
				new ParameterSpec("ipv6", "IPv6", "网络终端", "__IPV6__", OsSupport.BOTH, false),
				new ParameterSpec("ua", "User-Agent", "网络终端", "__UA__", OsSupport.BOTH, false),
				new ParameterSpec("model", "机型", "网络终端", "__MODEL__", OsSupport.BOTH, false),
				new ParameterSpec("osv", "系统版本", "网络终端", "__OSV__", OsSupport.BOTH, false),
				new ParameterSpec("ts", "时间戳", "网络终端", "__TS__", OsSupport.BOTH, false),
				new ParameterSpec("mts", "毫秒时间戳", "网络终端", "__MTS__", OsSupport.BOTH, false),
				new ParameterSpec("impresss_id", "曝光 ID", "平台路由", "__IMPRESSS_ID__", OsSupport.BOTH, false),
				new ParameterSpec("plan_id", "广告计划", "广告维度", "__ORDER_PLAN_ID__", OsSupport.BOTH, false),
				new ParameterSpec("group_id", "广告组", "广告维度", "__ORDER_GROUP_ID__", OsSupport.BOTH, false),
				new ParameterSpec("creative_id", "创意", "广告维度", "__CREATIVE_ID__", OsSupport.BOTH, false),
				new ParameterSpec("adv_id", "外部广告主", "广告维度", "__ADVERTISER_ID__", OsSupport.BOTH, false));

	private static final Set<String> DEFAULT_NON_DEVICE = Set.of("ip", "ua", "model", "ts", "mts");

	private final OcpxMonitorTaskMapper taskMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxPromotionLinkMapper linkMapper;

	private final OcpxMediaMapper mediaMapper;

	private final OcpxTrackingProperties trackingProperties;

	private final AdvertiserScope advertiserScope;

	private final JdbcTemplate jdbcTemplate;

	private final OcpxMonitorTaskTestHttpClient httpClient;

	private final SecureRandom random = new SecureRandom();

	public MonitorTaskTestViews.Preview generate(String taskId, MonitorTaskTestRequests.Generate request) {
		requireSystemAdministrator();
		Context context = context(taskId);
		String os = normalizeOs(request.getOs());
		Set<String> selected = selectedParameters(request.getParameters(), context, os);
		String requestId = nextRequestId();
		String callbackToken = randomHex(16);
		Map<String, String> values = randomValues(os);
		return preview(context, requestId, callbackToken, os, selected, values);
	}

	public MonitorTaskTestViews.Execution execute(String taskId, MonitorTaskTestRequests.Execute request) {
		requireSystemAdministrator();
		Context context = context(taskId);
		String os = normalizeOs(request.getOs());
		validateRequestId(request.getRequestId());
		validateCallbackToken(request.getCallbackToken());
		Map<String, String> values = normalizeValues(request.getParameters(), os);
		Set<String> selected = new LinkedHashSet<>(values.keySet());
		MonitorTaskTestViews.Preview validation = preview(context, request.getRequestId(), request.getCallbackToken(),
				os, selected, values);
		if (!validation.isExecutable()) {
			throw new BootBusinessException(String.join("；", validation.getBlockers()));
		}
		if (factExists(context.task().getId(), request.getRequestId())) {
			return new MonitorTaskTestViews.Execution(request.getRequestId(), validation.getTestUrl(), true, null, null,
					"ALREADY_EXECUTED", 0L, LocalDateTime.now(BUSINESS_ZONE));
		}
		URI testUri = buildUri(context, request.getRequestId(), request.getCallbackToken(), os, selected, values);
		OcpxMonitorTaskTestHttpClient.Response response = httpClient.get(testUri);
		return new MonitorTaskTestViews.Execution(request.getRequestId(), testUri.toASCIIString(), false,
				response.statusCode(), response.body(), response.errorCode(), response.durationMs(),
				LocalDateTime.now(BUSINESS_ZONE));
	}

	public MonitorTaskTestViews.Result result(String taskId, String requestId) {
		requireSystemAdministrator();
		Context context = context(taskId);
		validateRequestId(requestId);
		List<MonitorTaskTestViews.Fact> facts = jdbcTemplate.query("""
				SELECT event_code,billing_status,billing_reason_code,spend_amount,received_at
				FROM scx_ocpx_event_fact
				WHERE monitor_task_id=? AND advertiser_id=? AND request_id=? AND flow_type='FEEDBACK'
				ORDER BY received_at DESC LIMIT 1
				""",
				(rs, rowNum) -> new MonitorTaskTestViews.Fact(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getBigDecimal(4), time(rs.getTimestamp(5))),
				context.task().getId(), context.task().getAdvertiserId(), requestId);
		MonitorTaskTestViews.Fact fact = facts.isEmpty() ? null : facts.get(0);
		List<MonitorTaskTestViews.Flow> flows = jdbcTemplate.query("""
				SELECT result_status,partner_http_status,message,duration_ms,event_time
				FROM scx_ocpx_flow_log
				WHERE monitor_task_id=? AND advertiser_id=? AND event_id=? AND flow_type='FEEDBACK'
				ORDER BY create_time DESC,id DESC LIMIT 1
				""",
				(rs, rowNum) -> new MonitorTaskTestViews.Flow(rs.getString(1), (Integer) rs.getObject(2),
						rs.getString(3), rs.getLong(4), time(rs.getTimestamp(5))),
				context.task().getId(), context.task().getAdvertiserId(), requestId);
		MonitorTaskTestViews.Flow flow = flows.isEmpty() ? null : flows.get(0);
		List<MonitorTaskTestViews.CallbackFact> callbackFacts = jdbcTemplate.query("""
				SELECT event_code,billing_status,billing_reason_code,spend_amount,currency_amount,
				       deduction_decision,deduction_ratio_snapshot,deduction_decided_at,received_at
				FROM scx_ocpx_event_fact
				WHERE monitor_task_id=? AND advertiser_id=? AND request_id=? AND flow_type='CALLBACK'
				ORDER BY received_at DESC LIMIT 1
				""",
				(rs, rowNum) -> new MonitorTaskTestViews.CallbackFact(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getString(6), rs.getBigDecimal(7),
						time(rs.getTimestamp(8)), time(rs.getTimestamp(9))),
				context.task().getId(), context.task().getAdvertiserId(), requestId);
		MonitorTaskTestViews.CallbackFact callbackFact = callbackFacts.isEmpty() ? null : callbackFacts.get(0);
		MonitorTaskTestViews.Flow callbackFlow = callbackFlow(context, requestId, false);
		MonitorTaskTestViews.Flow downstreamFlow = callbackFlow(context, requestId, true);
		String state;
		String conclusion;
		if (fact == null) {
			state = "WAITING";
			conclusion = "Engine 尚未写入 Feedback 事实";
		}
		else if (fact.getBillingStatus() != null && fact.getBillingStatus().startsWith("REJECTED")) {
			state = "REJECTED";
			conclusion = "Feedback 已入站，但计费校验拒绝了上游投递";
		}
		else if (flow == null) {
			state = "UPSTREAM_PENDING";
			conclusion = "Feedback 已入站，上游投递结果仍待确认";
		}
		else if (!"SUCCESS".equals(flow.getResultStatus())) {
			state = "UPSTREAM_FAILED";
			conclusion = "Feedback 已入站，但上游投递失败";
		}
		else if (downstreamFlow != null && "SUCCESS".equals(downstreamFlow.getResultStatus())) {
			state = "COMPLETED";
			conclusion = "完整测试闭环已完成：SCX 测试回调接收器已收到最后一跳；不代表真实媒体已经入库";
		}
		else if (callbackFlow != null && "FAILED".equals(callbackFlow.getResultStatus())) {
			state = "CALLBACK_FAILED";
			conclusion = "广告主 Callback 已到达，但 SCX 处理失败";
		}
		else if (callbackFact == null) {
			state = "UPSTREAM_RESPONDED";
			conclusion = "上游点击接口已响应，等待广告主发送转化 Callback；HTTP 成功不代表合作方已经入库";
		}
		else if ("DEDUCTED".equals(callbackFact.getDeductionDecision())) {
			state = "CALLBACK_DEDUCTED";
			conclusion = "广告主 Callback 已到达，但媒体扣量规则拦截了最后一跳";
		}
		else if (!"PASSED".equals(callbackFact.getDeductionDecision())) {
			state = "CALLBACK_PROCESSING";
			conclusion = "广告主 Callback 已到达，正在等待计费和媒体扣量判定";
		}
		else {
			state = "DOWNSTREAM_PENDING";
			conclusion = "广告主 Callback 已处理并通过媒体扣量，等待测试回调接收器确认最后一跳";
		}
		return new MonitorTaskTestViews.Result(requestId, state, fact, flow, callbackFact, callbackFlow, downstreamFlow,
				conclusion, timeline(fact, flow, callbackFact, callbackFlow, downstreamFlow));
	}

	private List<MonitorTaskTestViews.TimelineItem> timeline(MonitorTaskTestViews.Fact fact,
			MonitorTaskTestViews.Flow flow, MonitorTaskTestViews.CallbackFact callbackFact,
			MonitorTaskTestViews.Flow callbackFlow, MonitorTaskTestViews.Flow downstreamFlow) {
		List<MonitorTaskTestViews.TimelineItem> timeline = new ArrayList<>();
		if (fact == null) {
			timeline.add(new MonitorTaskTestViews.TimelineItem("ENGINE_INGRESS", "WAITING", null, null,
					"等待 Engine 写入 Feedback 事实"));
			return List.copyOf(timeline);
		}
		timeline.add(new MonitorTaskTestViews.TimelineItem("ENGINE_INGRESS", "SUCCESS", fact.getReceivedAt(), null,
				"Feedback 事实已写入"));
		timeline.add(new MonitorTaskTestViews.TimelineItem("BILLING", fact.getBillingStatus(), fact.getReceivedAt(),
				null, fact.getBillingReasonCode()));
		if (flow == null) {
			timeline.add(new MonitorTaskTestViews.TimelineItem("UPSTREAM", "WAITING", null, null, "等待上游投递流程日志"));
		}
		else {
			timeline.add(new MonitorTaskTestViews.TimelineItem("UPSTREAM", flow.getResultStatus(), flow.getEventTime(),
					flow.getDurationMs(), flow.getMessage()));
		}
		if (callbackFact == null) {
			timeline.add(new MonitorTaskTestViews.TimelineItem("ADVERTISER_CALLBACK",
					callbackFlow == null ? "WAITING" : callbackFlow.getResultStatus(),
					callbackFlow == null ? null : callbackFlow.getEventTime(),
					callbackFlow == null ? null : callbackFlow.getDurationMs(),
					callbackFlow == null ? "等待广告主回传转化事件" : callbackFlow.getMessage()));
			return List.copyOf(timeline);
		}
		timeline.add(new MonitorTaskTestViews.TimelineItem("ADVERTISER_CALLBACK",
				callbackFlow == null ? "SUCCESS" : callbackFlow.getResultStatus(), callbackFact.getReceivedAt(),
				callbackFlow == null ? null : callbackFlow.getDurationMs(), callbackFact.getEventCode()));
		timeline.add(new MonitorTaskTestViews.TimelineItem("CALLBACK_BILLING", callbackFact.getBillingStatus(),
				callbackFact.getReceivedAt(), null, callbackFact.getBillingReasonCode()));
		timeline.add(new MonitorTaskTestViews.TimelineItem("MEDIA_DEDUCTION", callbackFact.getDeductionDecision(),
				callbackFact.getDeductionDecidedAt(), null, callbackFact.getDeductionRatio() == null ? null
						: "媒体回传比例 " + callbackFact.getDeductionRatio().stripTrailingZeros().toPlainString()));
		if ("DEDUCTED".equals(callbackFact.getDeductionDecision())) {
			timeline
				.add(new MonitorTaskTestViews.TimelineItem("DOWNSTREAM_CALLBACK", "SKIPPED", null, null, "媒体扣量规则已拦截"));
		}
		else if (downstreamFlow == null) {
			timeline.add(new MonitorTaskTestViews.TimelineItem("DOWNSTREAM_CALLBACK", "WAITING", null, null,
					"等待 SCX 测试回调接收器确认"));
		}
		else {
			timeline.add(new MonitorTaskTestViews.TimelineItem("DOWNSTREAM_CALLBACK", downstreamFlow.getResultStatus(),
					downstreamFlow.getEventTime(), downstreamFlow.getDurationMs(), downstreamFlow.getMessage()));
		}
		return List.copyOf(timeline);
	}

	private MonitorTaskTestViews.Preview preview(Context context, String requestId, String callbackToken, String os,
			Set<String> selected, Map<String, String> values) {
		List<String> blockers = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		validatePlatform(context.link().getPlatform(), os, blockers);
		Set<String> mediaMacros = macros(context.media().getMacroParams());
		Set<String> upstreamMacros = upstreamMacros(context);
		validateRenrenVideoSelection(context, os, selected, blockers);
		for (ParameterSpec spec : PARAMETER_SPECS) {
			if (selected.contains(spec.key()) && !spec.os().supports(os)) {
				blockers.add(spec.label() + " 与 " + os + " 不兼容");
			}
			if (selected.contains(spec.key()) && !mediaMacros.contains(spec.macro())) {
				warnings.add(spec.label() + " 未配置在媒体宏中，本次测试链接将临时补充该参数");
			}
		}
		boolean forwardableDevice = PARAMETER_SPECS.stream()
			.filter(ParameterSpec::device)
			.anyMatch(spec -> selected.contains(spec.key()) && spec.os().supports(os)
					&& upstreamMacros.contains(spec.macro()));
		if (!forwardableDevice) {
			blockers.add("当前选择没有可被上游模板转发的" + ("IOS".equals(os) ? "IDFA" : "Android 设备标识"));
		}
		if (!upstreamMacros.contains("__CLICKID__")) {
			warnings.add("广告主模板未配置 CLICKID，合作方可能无法按测试 ID 检索");
		}
		if (!upstreamMacros.contains("__OS__")) {
			warnings.add("广告主模板未配置 OS，合作方将无法直接识别测试平台");
		}
		if (!mediaMacros.contains("__CALLBACK_URL__")) {
			warnings.add("媒体宏未配置 CALLBACK_URL，本次完整测试将临时加入 SCX 测试回调接收器");
		}
		BigDecimal expectedCharge = expectedCharge(context.task());
		BigDecimal availableBalance = availableBalance(context.task().getAdvertiserId());
		if (expectedCharge.signum() > 0 && availableBalance == null) {
			blockers.add("广告主账户不存在，无法执行完整测试");
		}
		else if (expectedCharge.signum() > 0 && availableBalance.compareTo(expectedCharge) < 0) {
			blockers.add("广告主余额不足，无法保证完整测试中的点击或目标转化计费完成");
		}
		String callbackUrl = buildCallbackUrl(context, requestId, callbackToken);
		List<MonitorTaskTestViews.Parameter> parameters = parameterViews(requestId, callbackUrl, os, selected, values,
				mediaMacros, upstreamMacros);
		String testUrl = buildUri(context, requestId, callbackToken, os, selected, values).toASCIIString();
		return new MonitorTaskTestViews.Preview(requestId, "CLK", os, context.task().getId(),
				context.task().getMonitorName(), context.advertiser().getAdvertiserName(),
				context.media().getMediaName(), context.link().getLinkName(), context.link().getPlatform(), testUrl,
				callbackToken, expectedCharge, availableBalance, blockers.isEmpty(), List.copyOf(blockers),
				List.copyOf(warnings), parameters);
	}

	private List<MonitorTaskTestViews.Parameter> parameterViews(String requestId, String callbackUrl, String os,
			Set<String> selected, Map<String, String> values, Set<String> mediaMacros, Set<String> upstreamMacros) {
		List<MonitorTaskTestViews.Parameter> result = new ArrayList<>();
		result.add(new MonitorTaskTestViews.Parameter("request_id", "请求/测试 ID", "固定参数", "__REQUEST_ID__", true, false,
				true, true, false, requestId));
		result.add(new MonitorTaskTestViews.Parameter("clickid", "点击 ID", "固定参数", "__CLICKID__",
				mediaMacros.contains("__CLICKID__"), upstreamMacros.contains("__CLICKID__"), true, true, false,
				requestId));
		result.add(new MonitorTaskTestViews.Parameter("os", "操作系统", "固定参数", "__OS__", mediaMacros.contains("__OS__"),
				upstreamMacros.contains("__OS__"), true, true, false, "IOS".equals(os) ? "1" : "0"));
		result.add(new MonitorTaskTestViews.Parameter("callback_url", "SCX 测试回调接收器", "回调闭环", "__CALLBACK_URL__",
				mediaMacros.contains("__CALLBACK_URL__"), true, true, true, false, callbackUrl));
		for (ParameterSpec spec : PARAMETER_SPECS) {
			if (!spec.os().supports(os)) {
				continue;
			}
			boolean checked = selected.contains(spec.key());
			result.add(new MonitorTaskTestViews.Parameter(spec.key(), spec.label(), spec.group(), spec.macro(),
					mediaMacros.contains(spec.macro()), upstreamMacros.contains(spec.macro()), checked, false, true,
					checked ? values.get(spec.key()) : null));
		}
		return List.copyOf(result);
	}

	private Set<String> selectedParameters(List<String> requested, Context context, String os) {
		if (requested != null) {
			Set<String> selected = new LinkedHashSet<>(requested);
			validateKeys(selected);
			return selected;
		}
		Set<String> mediaMacros = macros(context.media().getMacroParams());
		Set<String> upstreamMacros = upstreamMacros(context);
		Set<String> defaults = new LinkedHashSet<>();
		for (ParameterSpec spec : PARAMETER_SPECS) {
			if (spec.os().supports(os) && mediaMacros.contains(spec.macro()) && upstreamMacros.contains(spec.macro())
					&& (spec.device() || DEFAULT_NON_DEVICE.contains(spec.key()))) {
				defaults.add(spec.key());
			}
		}
		return defaults;
	}

	private Map<String, String> normalizeValues(Map<String, String> requested, String os) {
		validateKeys(requested.keySet());
		Map<String, String> normalized = new LinkedHashMap<>();
		for (ParameterSpec spec : PARAMETER_SPECS) {
			if (!requested.containsKey(spec.key())) {
				continue;
			}
			String value = requested.get(spec.key());
			if (!StringUtils.hasText(value)) {
				throw new BootBusinessException(spec.label() + " 不能为空");
			}
			String trimmed = value.trim();
			validateValue(spec, trimmed, os);
			normalized.put(spec.key(), trimmed);
		}
		for (Map.Entry<String, String> relation : MD5_ORIGINALS.entrySet()) {
			String digest = normalized.get(relation.getKey());
			String original = normalized.get(relation.getValue());
			if (digest != null && original != null && !md5(original).equalsIgnoreCase(digest)) {
				throw new BootBusinessException(relation.getKey() + " 与原值不一致");
			}
		}
		return normalized;
	}

	private void validateKeys(Set<String> keys) {
		Set<String> allowed = new LinkedHashSet<>();
		PARAMETER_SPECS.forEach(spec -> allowed.add(spec.key()));
		for (String key : keys) {
			if (!allowed.contains(key)) {
				throw new BootBusinessException("不支持的测试参数: " + key);
			}
		}
	}

	private void validateValue(ParameterSpec spec, String value, String os) {
		if (!spec.os().supports(os)) {
			throw new BootBusinessException(spec.label() + " 与 " + os + " 不兼容");
		}
		if (value.length() > 1024 || value.chars().anyMatch(character -> Character.isISOControl(character))) {
			throw new BootBusinessException(spec.label() + " 格式无效");
		}
		boolean valid = switch (spec.key()) {
			case "imei" -> value.matches("\\d{15}") && validImei(value);
			case "imei_md5", "oaid_md5", "androidid_md5", "idfa_md5", "mac_md5" -> MD5.matcher(value).matches();
			case "oaid" -> OAID.matcher(value).matches();
			case "androidid" -> HEX_16.matcher(value).matches();
			case "aaid" -> UUID_VALUE.matcher(value).matches();
			case "idfa" -> IDFA.matcher(value).matches();
			case "mac" -> MAC.matcher(value).matches();
			case "ip" -> validIpv4(value);
			case "ipv6" -> value.contains(":") && value.length() <= 64;
			case "ts", "mts" -> value.matches("\\d{10,13}");
			case "ua", "model" -> value.length() <= 512;
			default -> value.length() <= 128;
		};
		if (!valid) {
			throw new BootBusinessException(spec.label() + " 格式无效");
		}
	}

	private Map<String, String> randomValues(String os) {
		String imei = imei();
		String oaid = randomHex(32);
		String androidId = randomHex(8);
		String idfa = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
		Map<String, String> values = new LinkedHashMap<>();
		values.put("imei", imei);
		values.put("imei_md5", md5(imei));
		values.put("oaid", oaid);
		values.put("oaid_md5", md5(oaid));
		values.put("androidid", androidId);
		values.put("androidid_md5", md5(androidId));
		values.put("aaid", UUID.randomUUID().toString());
		values.put("idfa", idfa);
		values.put("idfa_md5", md5(idfa));
		String caid = randomHex(32);
		values.put("caid", caid);
		values.put("caid_list", caid);
		values.put("caid1", caid);
		values.put("caid2", randomHex(32));
		values.put("last_caid", randomHex(32));
		values.put("prev_caid", randomHex(32));
		values.put("mac", randomMac());
		values.put("mac_md5", md5(values.get("mac")));
		values.put("ip", "203.0.113." + (random.nextInt(254) + 1));
		values.put("ipv6", "2001:db8::" + Integer.toHexString(random.nextInt(65535) + 1));
		values.put("ua", "IOS".equals(os)
				? "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
				: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
		values.put("model", "IOS".equals(os) ? "iPhone 15" : "Pixel 8");
		values.put("osv", "IOS".equals(os) ? "17.5" : "14");
		values.put("ts", Long.toString(System.currentTimeMillis() / 1000));
		values.put("mts", Long.toString(System.currentTimeMillis()));
		String suffix = randomHex(6);
		values.put("plan_id", "uat-plan-" + suffix);
		values.put("group_id", "uat-group-" + suffix);
		values.put("creative_id", "uat-creative-" + suffix);
		values.put("adv_id", "uat-advertiser-" + suffix);
		values.put("impresss_id", "uat-impression-" + suffix);
		return values;
	}

	private URI buildUri(Context context, String requestId, String callbackToken, String os, Set<String> selected,
			Map<String, String> values) {
		Map<String, String> parameters = new LinkedHashMap<>();
		parameters.put("request_id", requestId);
		parameters.put("callback_url", buildCallbackUrl(context, requestId, callbackToken));
		for (ParameterSpec spec : PARAMETER_SPECS) {
			if (selected.contains(spec.key())) {
				parameters.put(spec.key(), values.get(spec.key()));
			}
		}
		parameters.put("clickid", requestId);
		parameters.put("os", "IOS".equals(os) ? "1" : "0");
		parameters.put("event", "CLK");
		parameters.put("mId", context.task().getMediaId());
		parameters.put("tId", context.task().getId());
		parameters.put("aId", context.task().getAdvertiserId());
		parameters.put("lId", context.task().getPromotionLinkId());
		String query = parameters.entrySet()
			.stream()
			.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
			.reduce((left, right) -> left + "&" + right)
			.orElse("");
		try {
			URI base = engineBase();
			String baseValue = base.toString();
			String mediaPath = context.media().getUri().trim();
			if (!StringUtils.hasText(mediaPath) || mediaPath.contains("://") || mediaPath.contains("?")
					|| mediaPath.contains("#") || mediaPath.contains("\\") || mediaPath.contains("..")
					|| mediaPath.chars().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("unsafe Engine route");
			}
			String route = mediaPath.startsWith("/") ? mediaPath : "/" + mediaPath;
			URI target = URI.create(baseValue + route + "?" + query);
			if (!base.getScheme().equalsIgnoreCase(target.getScheme())
					|| !base.getHost().equalsIgnoreCase(target.getHost()) || base.getPort() != target.getPort()) {
				throw new IllegalArgumentException("Engine authority changed");
			}
			return target;
		}
		catch (RuntimeException exception) {
			throw new BootBusinessException("Engine 测试地址配置无效");
		}
	}

	private String buildCallbackUrl(Context context, String requestId, String callbackToken) {
		validateCallbackToken(callbackToken);
		try {
			URI base = engineBase();
			Map<String, String> parameters = new LinkedHashMap<>();
			parameters.put("request_id", requestId);
			parameters.put("token", callbackToken);
			parameters.put("mId", context.task().getMediaId());
			parameters.put("tId", context.task().getId());
			parameters.put("aId", context.task().getAdvertiserId());
			parameters.put("lId", context.task().getPromotionLinkId());
			String query = parameters.entrySet()
				.stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
			return URI.create(base + "/test-callback?" + query).toASCIIString();
		}
		catch (RuntimeException exception) {
			throw new BootBusinessException("Engine 测试回调地址配置无效");
		}
	}

	private URI engineBase() {
		String baseValue = trackingProperties.getEngineBaseUrl().trim().replaceAll("/+$", "");
		URI base = URI.create(baseValue);
		if (!Set.of("http", "https").contains(base.getScheme()) || !StringUtils.hasText(base.getHost())
				|| base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
			throw new IllegalArgumentException("unsafe Engine base URL");
		}
		return base;
	}

	private Context context(String taskId) {
		OcpxMonitorTask task = taskMapper.selectById(taskId);
		if (task == null) {
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "监测任务不存在");
		}
		OcpxAdvertiser advertiser = advertiserMapper.selectById(task.getAdvertiserId());
		OcpxPromotionLink link = linkMapper.selectById(task.getPromotionLinkId());
		OcpxMedia media = mediaMapper.selectById(task.getMediaId());
		if (advertiser == null || link == null || media == null) {
			throw new BootBusinessException("监测任务关联配置不完整");
		}
		return new Context(task, advertiser, link, media);
	}

	private void requireSystemAdministrator() {
		advertiserScope.requireSystemAdministrator();
	}

	private String normalizeOs(String os) {
		return os.trim().toUpperCase(Locale.ROOT);
	}

	private void validatePlatform(Integer platform, String os, List<String> blockers) {
		if (platform == null || platform == 4) {
			blockers.add("快应用或未知平台暂不支持点击测试");
		}
		else if (platform == 1 && !"ANDROID".equals(os)) {
			blockers.add("Android 任务只能生成 Android 测试参数");
		}
		else if (platform == 2 && !"IOS".equals(os)) {
			blockers.add("iOS 任务只能生成 iOS 测试参数");
		}
	}

	private BigDecimal expectedCharge(OcpxMonitorTask task) {
		return Boolean.TRUE.equals(task.getBillingEnabled()) && task.getUnitPrice() != null ? task.getUnitPrice()
				: BigDecimal.ZERO;
	}

	private BigDecimal availableBalance(String advertiserId) {
		List<BigDecimal> balances = jdbcTemplate.query("""
				SELECT available_balance FROM scx_ocpx_advertiser_account WHERE advertiser_id=?
				""", (rs, rowNum) -> rs.getBigDecimal(1), advertiserId);
		return balances.isEmpty() ? null : balances.get(0);
	}

	private boolean factExists(String taskId, String requestId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_event_fact
				WHERE monitor_task_id=? AND request_id=? AND event_code='CLK' AND flow_type='FEEDBACK'
				""", Long.class, taskId, requestId);
		return count != null && count > 0;
	}

	private Set<String> macros(String value) {
		Set<String> result = new LinkedHashSet<>();
		if (!StringUtils.hasText(value)) {
			return result;
		}
		for (ParameterSpec spec : PARAMETER_SPECS) {
			if (value.contains(spec.macro())) {
				result.add(spec.macro());
			}
		}
		for (String fixed : List.of("__REQUEST_ID__", "__CLICKID__", "__OS__", "__CALLBACK_URL__")) {
			if (value.contains(fixed)) {
				result.add(fixed);
			}
		}
		return result;
	}

	private Set<String> upstreamMacros(Context context) {
		if (context.advertiser().getUpstreamProtocol() != OcpxUpstreamProtocol.RENREN_VIDEO) {
			return macros(safe(context.advertiser().getBidUrl()) + "&" + safe(context.advertiser().getMacroParams()));
		}
		String template = safe(context.link().getLinkUrl());
		Set<String> result = macros(template);
		normalizeRenrenVideoMacro(result, template, "__IMEI__", "__IMEI_MD5__");
		normalizeRenrenVideoMacro(result, template, "__ANDROID_ID__", "__ANDROIDID__");
		normalizeRenrenVideoMacro(result, template, "__ANDROID_ID_MD5__", "__ANDROIDID_MD5__");
		normalizeRenrenVideoMacro(result, template, "__TS__", "__MTS__");
		normalizeRenrenVideoMacro(result, template, "__TS_S__", "__TS__");
		normalizeRenrenVideoMacro(result, template, "__REQID__", "__REQUEST_ID__");
		normalizeRenrenVideoMacro(result, template, "__CLICK_ID__", "__CLICKID__");
		if (template.contains("__CALLBACK__")) {
			result.add("__CALLBACK_URL__");
		}
		return result;
	}

	private void validateRenrenVideoSelection(Context context, String os, Set<String> selected, List<String> blockers) {
		if (context.advertiser().getUpstreamProtocol() != OcpxUpstreamProtocol.RENREN_VIDEO) {
			return;
		}
		try {
			RenrenVideoTemplateContract.requireValid(context.link().getLinkUrl(), context.link().getPlatform());
		}
		catch (IllegalArgumentException exception) {
			blockers.add("人人视频推广链接模板不符合实时点击协议");
			return;
		}
		for (String required : List.of("ip", "ua")) {
			if (!selected.contains(required)) {
				blockers.add("人人视频测试缺少必填参数 " + required);
			}
		}
		String template = context.link().getLinkUrl();
		String timestamp = template.contains("__TS_S__") ? "ts" : "mts";
		if (!selected.contains(timestamp)) {
			blockers.add("人人视频测试缺少必填时间戳 " + timestamp);
		}
		if ("ANDROID".equals(os)) {
			for (String required : List.of("imei_md5", "oaid", "oaid_md5")) {
				if (!selected.contains(required)) {
					blockers.add("人人视频 Android 测试缺少必填设备参数 " + required);
				}
			}
		}
		else if (Set.of("caid", "idfa", "idfa_md5").stream().noneMatch(selected::contains)) {
			blockers.add("人人视频 IOS 测试至少选择 CAID、IDFA 或 IDFA MD5 之一");
		}
	}

	private void normalizeRenrenVideoMacro(Set<String> result, String template, String partnerMacro,
			String canonicalMacro) {
		if (template.contains(partnerMacro)) {
			result.remove(partnerMacro);
			result.add(canonicalMacro);
		}
	}

	private void validateRequestId(String requestId) {
		if (!TEST_ID.matcher(requestId).matches()) {
			throw new BootBusinessException("测试 ID 格式无效");
		}
	}

	private void validateCallbackToken(String callbackToken) {
		if (callbackToken == null || !CALLBACK_TOKEN.matcher(callbackToken).matches()) {
			throw new BootBusinessException("测试回调令牌格式无效");
		}
	}

	private MonitorTaskTestViews.Flow callbackFlow(Context context, String requestId, boolean downstream) {
		String predicate = downstream ? "message = 'TEST_CALLBACK_RECEIVED'"
				: "message NOT IN ('TEST_CALLBACK_ARMED','TEST_CALLBACK_RECEIVED')";
		String sql = """
				SELECT result_status,partner_http_status,message,duration_ms,event_time
				FROM scx_ocpx_flow_log
				WHERE monitor_task_id=? AND advertiser_id=? AND event_id=? AND flow_type='CALLBACK'
				  AND %s
				ORDER BY create_time DESC,id DESC LIMIT 1
				""".formatted(predicate);
		List<MonitorTaskTestViews.Flow> flows = jdbcTemplate.query(sql,
				(rs, rowNum) -> new MonitorTaskTestViews.Flow(rs.getString(1), (Integer) rs.getObject(2),
						rs.getString(3), rs.getLong(4), time(rs.getTimestamp(5))),
				context.task().getId(), context.task().getAdvertiserId(), requestId);
		return flows.isEmpty() ? null : flows.get(0);
	}

	private String nextRequestId() {
		return "SCX-UAT-" + TEST_TIME.format(LocalDateTime.now(BUSINESS_ZONE)) + "-" + randomHex(6);
	}

	private String randomHex(int bytes) {
		byte[] value = new byte[bytes];
		random.nextBytes(value);
		return HexFormat.of().formatHex(value);
	}

	private String imei() {
		StringBuilder base = new StringBuilder(14);
		for (int index = 0; index < 14; index++) {
			base.append(random.nextInt(10));
		}
		int sum = 0;
		for (int index = 0; index < base.length(); index++) {
			int digit = base.charAt(index) - '0';
			if (index % 2 == 1) {
				digit *= 2;
				if (digit > 9) {
					digit -= 9;
				}
			}
			sum += digit;
		}
		return base.append((10 - sum % 10) % 10).toString();
	}

	private boolean validImei(String value) {
		int sum = 0;
		for (int index = 0; index < value.length(); index++) {
			int digit = value.charAt(index) - '0';
			if (index % 2 == 1) {
				digit *= 2;
				if (digit > 9) {
					digit -= 9;
				}
			}
			sum += digit;
		}
		return sum % 10 == 0;
	}

	private String md5(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("MD5 is unavailable", exception);
		}
	}

	private String randomMac() {
		byte[] bytes = new byte[6];
		random.nextBytes(bytes);
		bytes[0] = (byte) ((bytes[0] | 0x02) & 0xfe);
		StringBuilder result = new StringBuilder();
		for (byte value : bytes) {
			if (!result.isEmpty()) {
				result.append(':');
			}
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}

	private boolean validIpv4(String value) {
		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) {
			return false;
		}
		for (String part : parts) {
			if (!part.matches("\\d{1,3}")) {
				return false;
			}
			int number = Integer.parseInt(part);
			if (number > 255) {
				return false;
			}
		}
		return true;
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private LocalDateTime time(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private record Context(OcpxMonitorTask task, OcpxAdvertiser advertiser, OcpxPromotionLink link, OcpxMedia media) {

	}

	private record ParameterSpec(String key, String label, String group, String macro, OsSupport os, boolean device) {

	}

	private enum OsSupport {

		ANDROID,

		IOS,

		BOTH;

		private boolean supports(String os) {
			return this == BOTH || name().equals(os);
		}

	}

}
