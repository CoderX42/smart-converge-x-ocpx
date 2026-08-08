package com.yzytech.scx.ocpx.runtime.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.AdapterDeliveryResult;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.CallbackReqV1;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 通用下游 GET 回传适配器。
 *
 * <p>
 * 下游反馈入口仍复用 SCX 的标准 JSON/HMAC 协议；上游转化回传给渠道时，将归一化 CallbackReq 字段 安全替换到渠道配置的 HTTPS URL
 * 宏模板中。该适配器只约定宏、URL 编码和 2xx 成功语义，不假定任何 渠道私有签名、加密或响应体协议。
 * </p>
 */
@Component
public class TemplateGetDownstreamAdapter implements DownstreamAdapter {

	public static final Set<String> MACROS = Set.of("__EVENT__", "__EVENT_ID__", "__SOURCE_EVENT_ID__", "__CLICKID__",
			"__MONITOR_ID__", "__CHANNEL_ID__", "__AMOUNT__", "__AMOUNT_RAW__", "__TS__");

	private final ObjectMapper objectMapper;

	private final GetMacroTemplateRenderer templateRenderer;

	public TemplateGetDownstreamAdapter(ObjectMapper objectMapper, GetMacroTemplateRenderer templateRenderer) {
		this.objectMapper = objectMapper;
		this.templateRenderer = templateRenderer;
	}

	@Override
	public String code() {
		return "TEMPLATE_GET_DOWNSTREAM";
	}

	@Override
	public AdapterCapabilities capabilities() {
		return new AdapterCapabilities("DOWNSTREAM", Set.of(OcpxEventType.values()), Set.of("POST", "GET"),
				Set.of("HMAC"), Duration.ofSeconds(3), MACROS, true);
	}

	@Override
	public Mono<FeedbackReqV1> parseFeedback(String rawBody) {
		return Mono.fromCallable(() -> parseFeedbackRequest(rawBody));
	}

	private FeedbackReqV1 parseFeedbackRequest(String rawBody) {
		try {
			FeedbackReqV1 request = objectMapper.readValue(rawBody, FeedbackReqV1.class);
			if (request.getEvent() == null || request.getEvent().isBlank()) {
				throw new IllegalArgumentException("event is required");
			}
			return request;
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("invalid FeedbackReq payload", e);
		}
	}

	@Override
	public Mono<AdapterHttpRequest> buildCallback(RouteSnapshot route, CallbackReqV1 callback) {
		return Mono.fromCallable(() -> buildCallbackRequest(route, callback));
	}

	private AdapterHttpRequest buildCallbackRequest(RouteSnapshot route, CallbackReqV1 callback) {
		if (route.downstreamCallbackUrl() == null || route.downstreamCallbackUrl().isBlank()) {
			throw new IllegalArgumentException("downstream callback URL is not configured");
		}
		Map<String, String> values = new LinkedHashMap<>();
		values.put("__EVENT__", callback.event());
		values.put("__EVENT_ID__", callback.eventId());
		values.put("__SOURCE_EVENT_ID__", callback.sourceEventId());
		values.put("__CLICKID__", callback.clickid());
		values.put("__MONITOR_ID__", callback.monitorId());
		values.put("__CHANNEL_ID__", callback.channelId());
		values.put("__AMOUNT__", callback.amount() == null ? null : callback.amount().toPlainString());
		values.put("__AMOUNT_RAW__", callback.amountRaw());
		values.put("__TS__", callback.actionTime() == null ? null
				: Long.toString(callback.actionTime().toInstant().getEpochSecond()));
		String url = templateRenderer.render(route.downstreamCallbackUrl(), values, MACROS,
				Set.of("__EVENT__", "__EVENT_ID__"), code());
		return new AdapterHttpRequest("GET", URI.create(url), Map.of("Idempotency-Key", callback.eventId()), null);
	}

	@Override
	public Mono<AdapterDeliveryResult> evaluateCallbackResponse(AdapterHttpResponse response) {
		return Mono
			.fromSupplier(
					() -> response != null && response.statusCode() >= 200
							&& response.statusCode() < 300
									? AdapterDeliveryResult.success(null, null)
									: AdapterDeliveryResult.failure(
											response == null ? "TEMPLATE_GET_DOWNSTREAM_NO_RESPONSE"
													: "TEMPLATE_GET_DOWNSTREAM_HTTP_" + response.statusCode(),
											null, null));
	}

}
