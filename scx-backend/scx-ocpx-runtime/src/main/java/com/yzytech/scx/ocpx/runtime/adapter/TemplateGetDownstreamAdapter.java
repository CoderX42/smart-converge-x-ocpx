package com.yzytech.scx.ocpx.runtime.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterCapabilities;
import com.yzytech.scx.ocpx.contract.AdapterHttpRequest;
import com.yzytech.scx.ocpx.contract.AdapterHttpResponse;
import com.yzytech.scx.ocpx.contract.CallbackReqV1;
import com.yzytech.scx.ocpx.contract.DownstreamAdapter;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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

	public TemplateGetDownstreamAdapter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
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
	public FeedbackReqV1 parseFeedback(String rawBody) {
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
	public AdapterHttpRequest buildCallback(RouteSnapshot route, CallbackReqV1 callback) {
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
		String url = route.downstreamCallbackUrl();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			url = url.replace(entry.getKey(), encode(entry.getValue()));
		}
		if (url.matches(".*__[A-Z0-9_]+__.*")) {
			throw new IllegalArgumentException("downstream callback URL contains unsupported or unresolved macros");
		}
		return new AdapterHttpRequest("GET", URI.create(url), Map.of("Idempotency-Key", callback.eventId()), null);
	}

	@Override
	public boolean isSuccess(AdapterHttpResponse response) {
		return response.statusCode() >= 200 && response.statusCode() < 300;
	}

	private String encode(String value) {
		return value == null ? "" : UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
	}

}
