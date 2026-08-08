package com.yzytech.scx.ocpx.runtime.ingress;

import com.yzytech.scx.ocpx.runtime.error.OcpxException;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 渠道回传（feedback）入口控制器。
 *
 * <p>
 * 对外暴露 {@code POST /open-api/ocpx/v1/channels/{channelCode}/feedback}，接收渠道侧上报的
 * 曝光、点击等事件报文。本控制器只负责协议层的接收与响应状态映射，报文大小校验、解析、 路由定位、签名鉴权、幂等落库与投递封装全部委托给
 * {@link FeedbackIngressService}。
 * </p>
 *
 * <p>
 * <strong>线程模型：</strong>Service 返回 {@link Mono}，并在内部统一隔离 JDBC/Redis 阻塞片段。
 * </p>
 */
@RestController
@RequestMapping("/open-api/ocpx/v1/channels")
public class FeedbackIngressController {

	/** 回传接入业务服务，负责报文校验、鉴权、幂等与投递。 */
	private final FeedbackIngressService service;

	private final IngressAuditRecorder auditRecorder;

	public FeedbackIngressController(FeedbackIngressService service, IngressAuditRecorder auditRecorder) {
		this.service = service;
		this.auditRecorder = auditRecorder;
	}

	/**
	 * 接收指定渠道的回传报文。
	 *
	 * <p>
	 * 原始报文以字符串整体接收（不做绑定解析），连同请求方法、原始路径和请求头一起交给 服务层——这些都是签名校验的必要输入，不能在控制器层改写。
	 * </p>
	 *
	 * <p>
	 * 响应状态映射规则：
	 * <ul>
	 * <li>{@link IngressResult.Status#ACCEPTED}（新事件已受理）→ HTTP 202；</li>
	 * <li>{@link IngressResult.Status#DUPLICATE}（幂等命中，重复上报）→ HTTP 200，
	 * 告诉渠道"已收到、无需重试"；</li>
	 * <li>{@link IngressResult.Status#CONFLICT}（与已入库事件冲突）→ HTTP 409；</li>
	 * <li>{@link OcpxException}（鉴权失败、报文非法等业务错误）→ 异常自带的 HTTP 状态码， 响应体统一为 {@code {"code":
	 * ..., "message": ...}}。</li>
	 * </ul>
	 * </p>
	 * @param channelCode 渠道编码，来自路径变量，用于定位路由与下游适配器
	 * @param rawBody 请求体原始字符串，鉴权签名与报文解析均基于它，禁止预先反序列化
	 * @param headers 全部请求头，签名校验需要其中的时间戳、签名等字段
	 * @param exchange 当前请求上下文，用于获取原始请求路径（签名计算要素之一）
	 * @return 异步的 HTTP 响应，响应体为 {@link IngressResult} 或错误信息 Map
	 */
	@PostMapping("/{channelCode}/feedback")
	public Mono<ResponseEntity<Object>> feedback(@PathVariable String channelCode, @RequestBody String rawBody,
			@RequestHeader org.springframework.http.HttpHeaders headers, ServerWebExchange exchange) {
		// 取原始路径而非解析后的路径，保证与服务层验签时使用的签名要素一致
		String path = exchange.getRequest().getURI().getRawPath();
		long started = System.nanoTime();
		String remoteIp = exchange.getRequest().getRemoteAddress() == null ? null
				: exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
		return service.accept(channelCode, "POST", path, rawBody, headers).map(result -> {
			long durationMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
			if (result.status() == IngressResult.Status.CONFLICT) {
				auditRecorder.record(channelCode, "CONFLICT", HttpStatus.CONFLICT.value(), "event payload conflict",
						rawBody, headers, remoteIp, durationMs);
				return ResponseEntity.status(HttpStatus.CONFLICT).body((Object) result);
			}
			if (result.status() == IngressResult.Status.DUPLICATE) {
				auditRecorder.record(channelCode, "DUPLICATE", HttpStatus.OK.value(), "idempotent duplicate", rawBody,
						headers, remoteIp, durationMs);
			}
			// ACCEPTED → 202；DUPLICATE → 200（幂等重放视为成功，避免渠道无谓重试）
			return ResponseEntity
				.status(result.status() == IngressResult.Status.ACCEPTED ? HttpStatus.ACCEPTED : HttpStatus.OK)
				.body((Object) result);
		})
			// 业务异常统一转换为 {code, message} 结构，状态码取自异常本身
			.onErrorResume(OcpxException.class, error -> {
				auditRecorder.record(channelCode, auditStatus(error), error.getStatus().value(), error.getMessage(),
						rawBody, headers, remoteIp, Math.max(0, (System.nanoTime() - started) / 1_000_000));
				return Mono.just(ResponseEntity.status(error.getStatus())
					.body((Object) Map.of("code", error.getStatus().value(), "message", error.getMessage())));
			});
	}

	private String auditStatus(OcpxException error) {
		if (error.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
			return "RATE_LIMITED";
		}
		if (error.getStatus() == HttpStatus.UNAUTHORIZED || error.getStatus() == HttpStatus.FORBIDDEN) {
			return "AUTH_REJECTED";
		}
		return "PROTOCOL_REJECTED";
	}

}
