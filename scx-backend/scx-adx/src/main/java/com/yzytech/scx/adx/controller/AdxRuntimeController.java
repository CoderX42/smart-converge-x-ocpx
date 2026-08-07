package com.yzytech.scx.adx.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.RuntimeViews;
import com.yzytech.scx.adx.service.AdxRuntimeService;
import com.yzytech.scx.common.core.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OCPX 转发运行中心管理接口。
 *
 * <p>
 * 本控制器只负责管理端 HTTP 参数接收、Sa-Token 权限校验和统一 {@link Result} 响应封装。事件、投递任务、 入口审计及重放操作的查询边界和状态约束由
 * {@link AdxRuntimeService} 负责。所有读取接口统一要求 {@code adx:runtime:read}，会改变任务状态的重放接口要求
 * {@code adx:runtime:replay}。
 * </p>
 *
 * <p>
 * 对外管理端基础路径为 {@code /boot/adx/runtime}（其中 {@code /boot} 是 scx-boot context-path）。
 * 该组接口不返回原始请求体、签名、设备标识或完整 callback token，只展示运行审计中的脱敏摘要。
 * </p>
 */
@RestController
@RequestMapping("/adx/runtime")
public class AdxRuntimeController {

	private final AdxRuntimeService service;

	public AdxRuntimeController(AdxRuntimeService service) {
		this.service = service;
	}

	/**
	 * 查询运行中心概览。
	 *
	 * <p>
	 * 返回投递任务各状态数量、入口 DLQ 待处理数、Redis feedback stream 积压量以及按投递方向聚合的数量。 Redis 暂时不可用时，积压量使用
	 * {@code -1} 表示未知，不影响数据库统计返回。
	 * </p>
	 * @return OCPX 运行状态概览
	 */
	@GetMapping("/overview")
	@SaCheckPermission("adx:runtime:read")
	public Result<RuntimeViews.Overview> overview() {
		return Result.success(service.overview());
	}

	/**
	 * 分页查询异步投递任务。
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param status 可选任务状态，例如 PENDING、RETRY、SUCCESS、DEAD
	 * @param direction 可选投递方向：FEEDBACK_UPSTREAM 或 CALLBACK_DOWNSTREAM
	 * @return 按创建时间倒序排列的投递任务
	 */
	@GetMapping("/deliveries")
	@SaCheckPermission("adx:runtime:read")
	public Result<Page<RuntimeViews.Delivery>> deliveries(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String status, String direction) {
		return Result.success(service.deliveries(current, size, status, direction));
	}

	/**
	 * 分页查询 feedback 消费入口产生的死信记录。
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param status 可选死信状态，例如 PENDING 或 REPLAYED
	 * @return 入口 DLQ 记录
	 */
	@GetMapping("/ingress-dlq")
	@SaCheckPermission("adx:runtime:read")
	public Result<Page<RuntimeViews.IngressDlq>> ingressDlq(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String status) {
		return Result.success(service.ingressDlq(current, size, status));
	}

	/**
	 * 分页查询 OCPX 可靠事件事实。
	 *
	 * <p>
	 * 所有筛选条件均为精确匹配；时间范围按 {@code [startTime, endTime)} 处理。广告主账号访问时，服务层会强制
	 * 收敛到当前广告主，即使请求显式传入其他 {@code advertiserId} 也不能越权查询。
	 * </p>
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param eventId 可选业务事件 ID
	 * @param direction 可选事件来源方向：DOWNSTREAM_FEEDBACK 或 UPSTREAM_CALLBACK
	 * @param eventType 可选归一化事件类型，例如 CLICK、ACTIVATE、FIRST_ORDER、PAYMENT
	 * @param channelId 可选渠道主键
	 * @param advertiserId 可选广告主主键，受当前账号数据范围约束
	 * @param monitorId 可选监测链接主键
	 * @param status 可选事件处理状态
	 * @param startTime 可选接入开始时间，ISO-8601 格式，包含边界
	 * @param endTime 可选接入结束时间，ISO-8601 格式，不包含边界
	 * @return 事件事实分页结果
	 */
	@GetMapping("/events")
	@SaCheckPermission("adx:runtime:read")
	public Result<Page<RuntimeViews.Event>> events(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String eventId, String direction, String eventType,
			String channelId, String advertiserId, String monitorId, String status,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
		return Result.success(service.events(current, size, eventId, direction, eventType, channelId, advertiserId,
				monitorId, status, startTime, endTime));
	}

	/**
	 * 查询单个事件的完整处理时间线。
	 *
	 * <p>
	 * {@code id} 既可以是事件表内部主键，也可以是对外业务事件 ID。时间线合并 feedback 接入、CPC 账务、 上下游投递任务、每次 HTTP
	 * attempt、上游 callback 和扣量决策，并按发生时间升序返回。
	 * </p>
	 * @param id 事件内部主键或业务事件 ID
	 * @return 事件链路时间线；事件不存在或无权查看时返回统一失败结果
	 */
	@GetMapping("/events/{id}/timeline")
	@SaCheckPermission("adx:runtime:read")
	public Result<List<RuntimeViews.TimelineItem>> timeline(@PathVariable String id) {
		try {
			return Result.success(service.eventTimeline(id));
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	/**
	 * 查询指定投递任务的全部 HTTP 尝试。
	 *
	 * <p>
	 * 返回 HTTP 状态、耗时、目标 host/path 及脱敏请求/响应摘要，不返回原始 query、响应正文、签名或设备数据。
	 * 服务层同时根据投递源事件执行广告主数据范围校验。
	 * </p>
	 * @param id 投递任务 ID（delivery job id）
	 * @return 按 attempt 序号升序排列的投递尝试
	 */
	@GetMapping("/deliveries/{id}/attempts")
	@SaCheckPermission("adx:runtime:read")
	public Result<List<RuntimeViews.DeliveryAttempt>> attempts(@PathVariable String id) {
		return Result.success(service.deliveryAttempts(id));
	}

	/**
	 * 分页查询 feedback HTTP 入口的非正常请求审计。
	 *
	 * <p>
	 * 当前记录重复、冲突、验签失败、限流和协议拒绝。审计是异步、尽力而为的，不是事件可靠事实；默认保留 30 天。页面展示的 payload、IP
	 * 和错误原因均为哈希或脱敏值。
	 * </p>
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param channelCode 可选渠道编码
	 * @param resultStatus 可选审计结果，例如 DUPLICATE、CONFLICT、AUTH_REJECTED
	 * @param eventId 可选事件 ID
	 * @param routeReference 可选 monitor_id 或 task_id 路由引用
	 * @param startTime 可选审计开始时间，ISO-8601 格式，包含边界
	 * @param endTime 可选审计结束时间，ISO-8601 格式，不包含边界
	 * @return 入口异常审计分页结果
	 */
	@GetMapping("/ingress-audits")
	@SaCheckPermission("adx:runtime:read")
	public Result<Page<RuntimeViews.IngressAudit>> ingressAudits(@RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size, String channelCode, String resultStatus, String eventId,
			String routeReference, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
		return Result.success(service.ingressAudits(current, size, channelCode, resultStatus, eventId, routeReference,
				startTime, endTime));
	}

	/**
	 * 将死亡投递任务重新置为待投递。
	 *
	 * <p>
	 * 只有 {@code DEAD} 状态任务允许重放。操作会清空租约、尝试计数和最后错误，并把下次执行时间设为当前时间； 真正的外发仍由异步投递 worker 执行。
	 * </p>
	 * @param id 投递任务 ID
	 * @return 重放请求是否被接受；状态不允许时返回统一失败结果
	 */
	@PostMapping("/deliveries/{id}/replay")
	@SaCheckPermission("adx:runtime:replay")
	public Result<Boolean> replay(@PathVariable String id) {
		try {
			service.replayDelivery(id);
			return Result.success(true);
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	/**
	 * 将可恢复的入口死信重新写入对应 Redis feedback stream。
	 *
	 * <p>
	 * 仅 {@code PENDING} 死信允许重放；协议 envelope 本身无效的记录必须修正并重新调用接入接口，不能直接重放。 成功写入 stream
	 * 后，数据库状态更新为 {@code REPLAYED}。
	 * </p>
	 * @param id 入口 DLQ 记录 ID
	 * @return 重放请求是否被接受；记录不存在或不可重放时返回统一失败结果
	 */
	@PostMapping("/ingress-dlq/{id}/replay")
	@SaCheckPermission("adx:runtime:replay")
	public Result<Boolean> replayIngressDlq(@PathVariable String id) {
		try {
			service.replayIngressDlq(id);
			return Result.success(true);
		}
		catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

}
