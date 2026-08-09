package com.yzytech.scx.ocpx.runtime.adapter;

import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.AdapterDebugSupport;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 通用上游 GET 宏模板适配器。
 *
 * <p>
 * <strong>它在链路中的位置：</strong>渠道先把标准 {@code FeedbackReqV1} 发送给 SCX，运行时完成鉴权、 路由、幂等落库和（CLICK
 * 场景下的）计费后，再由本适配器把 SHOW/CLICK 转换成广告主配置的 GET 请求。 广告主稍后携带 {@code ext} 回调 SCX
 * 时，{@link #parseCallback(String, Map)} 将其归一化为转化事件，后续 再由下游适配器回传给渠道。
 * </p>
 *
 * <p>
 * <strong>通用边界：</strong>本类只统一四件事：宏取值、每个查询参数值的 URL 编码、HTTP 2xx 成功语义， 以及常见 query callback
 * 的归一化。合作方私有签名、加密、特殊响应体判定或非 GET 协议必须实现独立 {@link UpstreamAdapter}，不能把私有规则继续堆入本类。
 * </p>
 *
 * <p>
 * <strong>安全与隐私：</strong>{@code __CALLBACK__}/{@code __CALLBACK_URL__} 注入的是服务端生成的短期
 * 回调地址，其中 {@code ext} 只是不可猜测且带签名的引用，不包含原始设备字段或完整反馈报文。所有宏值 均作为 query parameter value
 * 单独编码，模板中的 scheme、host 和 path 则在路由发布阶段受 HTTPS 与 域名白名单约束。
 * </p>
 */
@Component
public class TemplateGetUpstreamAdapter implements UpstreamAdapter, AdapterDebugSupport {

	/** 只有曝光和点击属于“渠道到广告主”的反馈；转化事件走相反方向的 callback。 */
	private static final Set<OcpxEventType> FEEDBACK_EVENTS = Set.of(OcpxEventType.SHOW, OcpxEventType.CLICK);

	/**
	 * 允许出现在广告主 bidUrl 中的宏白名单。
	 *
	 * <p>
	 * 同时保留 {@code __IMEI_MD5__}/{@code __IMEI__MD5__} 等历史别名，是为了兼容不同上游模板的
	 * 命名习惯；两个别名来自同一个已归一化字段，不会改变数据含义。未知宏会在发布校验或构建请求时被拒绝。
	 * </p>
	 */
	private static final Set<String> MACROS = Set.of("__EVENT__", "__EVENT_TIME__", "__EVENT_ID__", "__TS__", "__MTS__",
			"__REQUEST_ID__", "__CHANNEL_ID__", "__DSP_ID__", "__LINK_ID__", "__TASK_ID__", "__MONITOR_ID__",
			"__BIZ_ID__", "__CAMPAIGN_ID__", "__ADGROUP_ID__", "__CREATIVE_ID__", "__CREATIVEID__", "__CLICKID__",
			"__IDFA__", "__IDFA_MD5__", "__IDFA__MD5__", "__IMEI__", "__IMEI_MD5__", "__IMEI__MD5__", "__OAID__",
			"__OAID_MD5__", "__OAID__MD5__", "__CAID__", "__CAID_LIST__", "__ANDROID_ID__", "__ANDROID_ID_MD5__",
			"__AAID__", "__GAID__", "__IDFV__", "__IP__", "__UA__", "__OS__", "__MODEL__", "__CALLBACK__",
			"__CALLBACK_URL__");

	private final GetMacroTemplateRenderer templateRenderer;

	public TemplateGetUpstreamAdapter(GetMacroTemplateRenderer templateRenderer) {
		this.templateRenderer = templateRenderer;
	}

	@Override
	public String code() {
		return "TEMPLATE_GET";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("UPSTREAM", FEEDBACK_EVENTS, Set.of("GET"), Set.of("NONE"),
				Duration.ofSeconds(3), MACROS, true);
	}

	@Override
	public boolean supports(OcpxEventType eventType) {
		return FEEDBACK_EVENTS.contains(eventType);
	}

	@Override
	public FeedbackReqV1 sampleFeedback(RouteSnapshot route, String executionId) {
		FeedbackReqV1 request = new FeedbackReqV1();
		request.setEvent("CLICK");
		request.setEventId("debug-" + executionId);
		request.setClickid("debug-click-" + executionId);
		request.setEventTime(System.currentTimeMillis() / 1000);
		request.setOs("android");
		request.setOaid("debug-device-" + executionId);
		request.setIp("203.0.113.10");
		request.setUa("SCX-API-Debugger/1.0");
		request.setModel("SCX-Debug-Device");
		request.setCampaignId("debug-campaign");
		request.setAdgroupId("debug-adgroup");
		request.setCreativeId("debug-creative");
		request.setAdvertiserId(route.advertiserId());
		return request;
	}

	@Override
	public Map<String, String> sampleCallback(RouteSnapshot route, String callbackToken, String executionId) {
		return Map.of("ext", callbackToken, "s", route.channelId() + "_" + route.routeId(), "action_type", "ACTIVATE",
				"conversion_id", "debug-" + executionId);
	}

	@Override
	public Mono<AdapterHttpRequest> buildFeedback(RouteSnapshot route, FeedbackReqV1 feedback, String callbackUrl) {
		return Mono.fromCallable(() -> buildFeedbackRequest(route, feedback, callbackUrl));
	}

	private AdapterHttpRequest buildFeedbackRequest(RouteSnapshot route, FeedbackReqV1 feedback, String callbackUrl) {
		OcpxEventType eventType = OcpxEventType.fromExternal(feedback.getEvent());
		if (!supports(eventType)) {
			throw new IllegalArgumentException("TEMPLATE_GET only supports show and click feedback");
		}
		Map<String, String> values = new LinkedHashMap<>();
		// event_id 是 SCX 的首选幂等标识；老渠道只传 clickid 时，用 clickid 保持请求可追踪、可重试。
		String requestId = firstNonBlank(feedback.getEventId(), feedback.getClickid());
		values.put("__EVENT__", eventType.name().toLowerCase());
		values.put("__EVENT_TIME__", feedback.getEventTime() == null ? null : feedback.getEventTime().toString());
		values.put("__EVENT_ID__", requestId);
		// __TS__ 固定为秒，__MTS__ 固定为毫秒；输入既可为秒也可为毫秒，避免合作方重复换算。
		values.put("__TS__", epochSeconds(feedback.getEventTime()));
		values.put("__MTS__", epochMillis(feedback.getEventTime()));
		values.put("__REQUEST_ID__", requestId);
		values.put("__CHANNEL_ID__", feedback.getChannelId());
		values.put("__DSP_ID__", feedback.getDspId());
		values.put("__LINK_ID__", feedback.getLinkId());
		values.put("__TASK_ID__", feedback.getTaskId());
		values.put("__MONITOR_ID__", feedback.getMonitorId());
		// biz_id 是发布路由的稳定业务别名；未配置时退回监测 ID，保证老模板仍能定位任务。
		values.put("__BIZ_ID__", firstNonBlank(route.bizId(), feedback.getMonitorId()));
		values.put("__CAMPAIGN_ID__", feedback.getCampaignId());
		values.put("__ADGROUP_ID__", feedback.getAdgroupId());
		values.put("__CREATIVE_ID__", feedback.getCreativeId());
		values.put("__CREATIVEID__", feedback.getCreativeId());
		values.put("__CLICKID__", feedback.getClickid());
		values.put("__IDFA__", feedback.getIdfa());
		values.put("__IDFA_MD5__", feedback.getIdfaMd5());
		values.put("__IDFA__MD5__", feedback.getIdfaMd5());
		values.put("__IMEI__", feedback.getImei());
		values.put("__IMEI_MD5__", feedback.getImeiMd5());
		values.put("__IMEI__MD5__", feedback.getImeiMd5());
		values.put("__OAID__", feedback.getOaid());
		values.put("__OAID_MD5__", feedback.getOaidMd5());
		values.put("__OAID__MD5__", feedback.getOaidMd5());
		values.put("__CAID__", feedback.getCaid());
		values.put("__CAID_LIST__", feedback.getCaid());
		values.put("__ANDROID_ID__", feedback.getAndroidId());
		values.put("__ANDROID_ID_MD5__", feedback.getAndroidIdMd5());
		values.put("__AAID__", feedback.getAaid());
		values.put("__GAID__", feedback.getGaid());
		values.put("__IDFV__", feedback.getIdfv());
		values.put("__IP__", feedback.getIp());
		values.put("__UA__", feedback.getUa());
		values.put("__OS__", feedback.getOs());
		values.put("__MODEL__", feedback.getModel());
		values.put("__CALLBACK__", callbackUrl);
		values.put("__CALLBACK_URL__", callbackUrl);

		// upstreamRequiredMacros 只约束业务上声明“必填”的宏；非必填缺值会替换为空字符串。
		Set<String> required = requiredMacros(route.upstreamRequiredMacros());
		String url = templateRenderer.render(route.bidUrl(), values, MACROS, required, code());
		return new AdapterHttpRequest("GET", URI.create(url), Map.of(), null);
	}

	@Override
	public Mono<AdapterDeliveryResult> evaluateFeedbackResponse(AdapterHttpResponse response) {
		return Mono.fromSupplier(() -> response != null && response.statusCode() >= 200 && response.statusCode() < 300
				? AdapterDeliveryResult.success(null, null)
				: AdapterDeliveryResult.failure(
						response == null ? "TEMPLATE_GET_NO_RESPONSE" : "TEMPLATE_GET_HTTP_" + response.statusCode(),
						null, null));
	}

	@Override
	public Mono<String> callbackToken(Map<String, String> parameters) {
		return Mono.justOrEmpty(parameters.get("ext"));
	}

	@Override
	public Mono<NormalizedCallbackEvent> parseCallback(String callbackRef, Map<String, String> parameters) {
		return Mono.fromCallable(() -> parseCallbackEvent(callbackRef, parameters));
	}

	private NormalizedCallbackEvent parseCallbackEvent(String callbackRef, Map<String, String> parameters) {
		String eventValue = firstNonBlank(parameters.get("action_type"), parameters.get("event"));
		if (eventValue == null) {
			throw new IllegalArgumentException("event is required");
		}
		OcpxEventType eventType = OcpxEventType.fromExternalOrUnknown(eventValue);
		if (eventType == OcpxEventType.SHOW || eventType == OcpxEventType.CLICK) {
			throw new IllegalArgumentException("upstream callback must be a conversion event");
		}
		String amountRaw = firstNonBlank(parameters.get("currency_amount"), parameters.get("amount"));
		BigDecimal amount = parseAmount(amountRaw);
		OffsetDateTime actionTime = parseActionTime(
				firstNonBlank(parameters.get("event_time"), parameters.get("action_time")));
		String externalEventId = firstNonBlank(parameters.get("conversion_id"), parameters.get("event_id"),
				parameters.get("order_id"));
		String identity;
		if (externalEventId != null) {
			// 合作方给出转化 ID 时，它与 callbackRef、事件类型共同构成稳定幂等身份。
			identity = callbackRef + "|" + eventType + "|" + externalEventId;
		}
		else {
			// 没有外部 ID 时对业务参数排序后散列；去掉 SCX 自己的 ext/s，避免签名轮换破坏幂等性。
			Map<String, String> identityParameters = new TreeMap<>(parameters);
			identityParameters.remove("ext");
			identityParameters.remove("s");
			identity = callbackRef + "|" + identityParameters;
		}
		return new NormalizedCallbackEvent(OcpxHashing.sha256(identity), null, callbackRef, eventType, actionTime,
				amount, amountRaw).withSourceEventCode(eventValue.trim());
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackSuccess(Map<String, String> parameters, String eventId,
			String status) {
		return Mono.fromSupplier(() -> {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("code", 0);
			body.put("event_id", eventId);
			body.put("status", status);
			return new UpstreamCallbackResponse(200, Map.of(), body);
		});
	}

	@Override
	public Mono<UpstreamCallbackResponse> callbackFailure(Map<String, String> parameters, int httpStatus,
			String message) {
		return Mono.fromSupplier(() -> {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("code", 1);
			body.put("message", message);
			return new UpstreamCallbackResponse(httpStatus, Map.of(), body);
		});
	}

	public static Set<String> supportedMacros() {
		// 发布服务复用同一白名单校验模板，避免“配置可保存、运行时却不能发送”的契约漂移。
		return MACROS;
	}

	private Set<String> requiredMacros(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(value.split("[,\\s]+")).filter(item -> !item.isBlank()).collect(Collectors.toSet());
	}

	private BigDecimal parseAmount(String value) {
		if (value == null) {
			return null;
		}
		if (value.length() > 64) {
			throw new IllegalArgumentException("amount is too long");
		}
		try {
			BigDecimal candidate = new BigDecimal(value);
			int integerDigits = candidate.precision() - candidate.scale();
			return candidate.signum() >= 0 && candidate.scale() <= 6 && integerDigits <= 14 ? candidate : null;
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}

	private OffsetDateTime parseActionTime(String value) {
		if (value == null) {
			return OffsetDateTime.now();
		}
		try {
			if (value.matches("\\d+")) {
				long epoch = Long.parseLong(value);
				long millis = epoch < 10_000_000_000L ? Math.multiplyExact(epoch, 1000) : epoch;
				return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
			}
			return OffsetDateTime.parse(value);
		}
		catch (ArithmeticException | DateTimeParseException | NumberFormatException e) {
			throw new IllegalArgumentException("callback event_time is invalid", e);
		}
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String epochSeconds(Long eventTime) {
		if (eventTime == null) {
			return null;
		}
		return Long.toString(eventTime < 10_000_000_000L ? eventTime : eventTime / 1000);
	}

	private String epochMillis(Long eventTime) {
		if (eventTime == null) {
			return null;
		}
		return Long.toString(eventTime < 10_000_000_000L ? Math.multiplyExact(eventTime, 1000) : eventTime);
	}

}
