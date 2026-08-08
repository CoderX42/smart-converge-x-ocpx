package com.yzytech.scx.ocpx.runtime.callback;

import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.ocpx.contract.UpstreamCallbackResponse;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterNotFoundException;
import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 通用上游 callback 调度器。
 *
 * <p>
 * 本类只负责三件事：根据上游编码选择适配器、调用统一的 callback 入站服务、让适配器生成当前媒体要求的 HTTP 响应。它不识别任何 Coco
 * 或其他媒体的私有参数，也不拼装固定的成功/失败 Body。
 * </p>
 */
@Service
public class UpstreamCallbackDispatcher {

	private final CallbackIngressService callbackIngressService;

	private final AdapterRegistry adapterRegistry;

	public UpstreamCallbackDispatcher(CallbackIngressService callbackIngressService, AdapterRegistry adapterRegistry) {
		this.callbackIngressService = callbackIngressService;
		this.adapterRegistry = adapterRegistry;
	}

	/**
	 * 将一次 callback 请求交给对应的上游适配器处理。
	 * @param upstreamCode URL 中的上游编码；匹配时忽略大小写
	 * @param parameters 上游提交的 query 参数
	 * @return 已找到适配器时包含媒体响应，未注册上游时返回 not-found 结果
	 */
	public Mono<DispatchResult> dispatch(String upstreamCode, Map<String, String> parameters) {
		return Mono.defer(() -> {
			UpstreamAdapter adapter = findAdapter(upstreamCode);
			if (adapter == null) {
				return Mono.just(DispatchResult.upstreamNotFound());
			}

			Map<String, String> safeParameters = immutableParameters(parameters);
			return callbackIngressService.accept(adapter, safeParameters)
				.flatMap(result -> adapter.callbackSuccess(safeParameters, result.eventId(), result.status()))
				.map(DispatchResult::handled)
				.onErrorResume(OcpxException.class,
						error -> adapter.callbackFailure(safeParameters, error.getStatus().value(), error.getMessage())
							.map(DispatchResult::handled));
		});
	}

	/**
	 * 只在适配器查找阶段处理“未注册”异常，避免把适配器业务代码中的参数异常错误地转换成 404。
	 */
	private UpstreamAdapter findAdapter(String upstreamCode) {
		if (upstreamCode == null || upstreamCode.trim().isEmpty()) {
			return null;
		}
		try {
			return adapterRegistry.requireUpstream(upstreamCode.trim().toUpperCase(Locale.ROOT));
		}
		catch (AdapterNotFoundException error) {
			return null;
		}
	}

	/**
	 * 同一次请求的参数会先复制为只读 Map，防止某个适配器修改参数后影响后续的成功或失败响应。
	 */
	private Map<String, String> immutableParameters(Map<String, String> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<String, String>(parameters));
	}

	/** 通用调度结果；Controller 只根据是否找到上游决定返回 404 或适配器响应。 */
	public static final class DispatchResult {

		private final boolean upstreamFound;

		private final UpstreamCallbackResponse response;

		private DispatchResult(boolean upstreamFound, UpstreamCallbackResponse response) {
			this.upstreamFound = upstreamFound;
			this.response = response;
		}

		static DispatchResult upstreamNotFound() {
			return new DispatchResult(false, null);
		}

		static DispatchResult handled(UpstreamCallbackResponse response) {
			if (response == null) {
				throw new IllegalArgumentException("upstream callback response must not be null");
			}
			return new DispatchResult(true, response);
		}

		public boolean isUpstreamFound() {
			return upstreamFound;
		}

		public UpstreamCallbackResponse getResponse() {
			if (response == null) {
				throw new IllegalStateException("no callback response for an unregistered upstream");
			}
			return response;
		}

	}

}
