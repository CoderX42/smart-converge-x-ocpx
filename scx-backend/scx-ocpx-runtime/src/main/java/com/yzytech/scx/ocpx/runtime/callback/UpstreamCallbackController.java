package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import com.yzytech.scx.ocpx.runtime.ingress.IngressAuditRecorder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 所有已注册上游适配器共用的 callback HTTP 入口。
 *
 * <p>
 * 本控制器只接收 HTTP 请求并输出调度结果。适配器选择和通用处理流程由 {@link UpstreamCallbackDispatcher} 负责；callback
 * token 提取、参数解析、业务事件映射以及成功/失败响应格式由具体的上游适配器负责。
 * </p>
 */
@RestController
@RequestMapping("/open-api/ocpx/v1/upstreams")
public class UpstreamCallbackController {

	private final UpstreamCallbackDispatcher dispatcher;

	private final IngressAuditRecorder auditRecorder;

	public UpstreamCallbackController(UpstreamCallbackDispatcher dispatcher, IngressAuditRecorder auditRecorder) {
		this.dispatcher = dispatcher;
		this.auditRecorder = auditRecorder;
	}

	/**
	 * 接收任意已注册上游的 GET callback。
	 *
	 * <p>
	 * 该方法不读取任何媒体私有字段。未注册的 {@code upstreamCode} 返回 HTTP 404；找到适配器后，HTTP 状态、响应头和 Body
	 * 都直接使用适配器生成的结果。
	 * </p>
	 */
	@GetMapping("/{upstreamCode}/callback")
	public Mono<ResponseEntity<Object>> callback(@PathVariable String upstreamCode,
			@RequestParam Map<String, String> parameters, @RequestHeader HttpHeaders headers,
			ServerWebExchange exchange) {
		long started = System.nanoTime();
		String remoteIp = remoteIp(exchange);
		return dispatcher.dispatch(upstreamCode, parameters).map(result -> {
			if (!result.isUpstreamFound()) {
				recordAudit(upstreamCode, "UPSTREAM_NOT_FOUND", HttpStatus.NOT_FOUND.value(),
						"upstream adapter is not registered", parameters, headers, remoteIp, started);
				return ResponseEntity.notFound().build();
			}
			UpstreamCallbackResponse response = result.getResponse();
			if (result.isRejected()) {
				recordAudit(upstreamCode, auditStatus(result.getRejectionStatusCode()), response.statusCode(),
						result.getRejectionReason(), parameters, headers, remoteIp, started);
			}
			return toHttpResponse(response);
		})
			.doOnError(error -> recordAudit(upstreamCode, "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR.value(),
					error.getMessage(), parameters, headers, remoteIp, started));
	}

	/**
	 * 将 callback 异常交给异步审计器。审计器只保存参数摘要和脱敏原因，且自身失败不会改变媒体响应。
	 */
	private void recordAudit(String upstreamCode, String resultStatus, int httpStatus, String reason,
			Map<String, String> parameters, HttpHeaders headers, String remoteIp, long started) {
		long durationMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
		auditRecorder.recordUpstreamCallback(upstreamCode, resultStatus, httpStatus, reason, parameters, headers,
				remoteIp, durationMs);
	}

	/** 按 SCX 内部拒绝原因分类；媒体适配器最终返回的 HTTP 状态不参与这个分类。 */
	private String auditStatus(int rejectionStatusCode) {
		if (rejectionStatusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
			return "RATE_LIMITED";
		}
		if (rejectionStatusCode == HttpStatus.UNAUTHORIZED.value()
				|| rejectionStatusCode == HttpStatus.FORBIDDEN.value()) {
			return "AUTH_REJECTED";
		}
		return rejectionStatusCode >= 500 ? "INTERNAL_ERROR" : "PROTOCOL_REJECTED";
	}

	private String remoteIp(ServerWebExchange exchange) {
		if (exchange == null || exchange.getRequest().getRemoteAddress() == null
				|| exchange.getRequest().getRemoteAddress().getAddress() == null) {
			return null;
		}
		return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
	}

	/** 将 adapter 定义的状态、headers 和任意 Body 原样写入 Spring HTTP 响应。 */
	private ResponseEntity<Object> toHttpResponse(UpstreamCallbackResponse response) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.statusCode());
		for (Map.Entry<String, String> header : response.headers().entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}
		return builder.body(response.body());
	}

}
