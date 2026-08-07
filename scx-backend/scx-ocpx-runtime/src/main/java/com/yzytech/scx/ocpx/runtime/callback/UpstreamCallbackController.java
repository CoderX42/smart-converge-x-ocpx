package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

	public UpstreamCallbackController(UpstreamCallbackDispatcher dispatcher) {
		this.dispatcher = dispatcher;
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
	public ResponseEntity<Object> callback(@PathVariable String upstreamCode,
			@RequestParam Map<String, String> parameters) {
		UpstreamCallbackDispatcher.DispatchResult result = dispatcher.dispatch(upstreamCode, parameters);
		if (!result.isUpstreamFound()) {
			return ResponseEntity.notFound().build();
		}
		return toHttpResponse(result.getResponse());
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
