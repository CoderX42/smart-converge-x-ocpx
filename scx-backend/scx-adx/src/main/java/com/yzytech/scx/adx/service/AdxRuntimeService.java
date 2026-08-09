package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.RuntimeViews;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * OCPX 转发运行中心的查询与运维服务。
 *
 * <p>
 * 该服务构建管理后台所需的只读运行视图，数据来自 OCPX 事件事实、投递任务、投递尝试、入口审计、扣量决策、 CPC 账务以及 Redis feedback
 * streams。这里有意使用 {@link JdbcTemplate} 组合跨表查询，避免为仅供运维查看的 聚合视图额外建立一组可写实体。
 * </p>
 *
 * <p>
 * 数据权限由 {@link AdxAccessScope} 统一收敛：广告主账号只能读取自身事件及其关联 attempts/入口审计；管理员和
 * 运营账号按已有权限查看全局运行状态。返回内容只包含运行审计的脱敏摘要，不负责读取或还原协议快照中的敏感原文。
 * </p>
 */
@Service
public class AdxRuntimeService {

	private final JdbcTemplate jdbcTemplate;

	private final StringRedisTemplate redisTemplate;

	private final int partitionCount;

	private final AdxAccessScope accessScope;

	/**
	 * 创建运行中心服务。
	 * @param jdbcTemplate 查询 OCPX 与 ADX 运行表
	 * @param redisTemplate 查询 feedback stream 积压并执行 DLQ 重放
	 * @param partitionCount feedback stream 分区数量，必须与数据面配置一致
	 * @param accessScope 当前管理账号的广告主数据范围
	 */
	public AdxRuntimeService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
			@Value("${scx.ocpx.partition-count:64}") int partitionCount, AdxAccessScope accessScope) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
		this.partitionCount = partitionCount;
		this.accessScope = accessScope;
	}

	/**
	 * 聚合运行中心顶部统计卡片。
	 *
	 * <p>
	 * 数据库统计以投递任务当前状态为准；入口 DLQ 仅统计待处理记录；Redis 积压是所有 feedback 分区 stream 当前长度之和。 Redis
	 * 查询失败时不会让整个概览接口失败，而是使用 {@code -1} 明确表示积压量未知。
	 * </p>
	 * @return 任务状态、方向、DLQ 和 stream 积压概览
	 */
	public RuntimeViews.Overview overview() {
		Map<String, Long> statuses = new LinkedHashMap<>();
		jdbcTemplate.query(
				"SELECT status,COUNT(*) FROM scx_ocpx_delivery_job WHERE debug_execution_id IS NULL GROUP BY status",
				(RowCallbackHandler) rs -> statuses.put(rs.getString(1), rs.getLong(2)));
		Map<String, Long> directions = new LinkedHashMap<>();
		jdbcTemplate.query(
				"SELECT direction,COUNT(*) FROM scx_ocpx_delivery_job WHERE debug_execution_id IS NULL GROUP BY direction",
				(RowCallbackHandler) rs -> directions.put(rs.getString(1), rs.getLong(2)));
		Long dlq = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_ingress_dlq WHERE status='PENDING'",
				Long.class);
		return new RuntimeViews.Overview(statuses.getOrDefault("PENDING", 0L), statuses.getOrDefault("RETRY", 0L),
				statuses.getOrDefault("DEAD", 0L), statuses.getOrDefault("SUCCESS", 0L), dlq == null ? 0 : dlq,
				feedbackPending(), directions);
	}

	/**
	 * 分页查询上下游异步投递任务。
	 *
	 * <p>
	 * {@code status} 和 {@code direction} 均为可选精确条件。分页参数最后追加到预编译参数列表，所有用户输入均通过 JDBC
	 * 占位符绑定，不拼接到 SQL 值中。
	 * </p>
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param status 可选任务状态
	 * @param direction 可选投递方向
	 * @return 按创建时间倒序排列的投递任务分页
	 */
	public Page<RuntimeViews.Delivery> deliveries(long current, long size, String status, String direction) {
		String where = " WHERE 1=1" + (StringUtils.hasText(status) ? " AND status=?" : "")
				+ (StringUtils.hasText(direction) ? " AND direction=?" : "");
		List<Object> args = new java.util.ArrayList<>();
		if (StringUtils.hasText(status))
			args.add(status);
		if (StringUtils.hasText(direction))
			args.add(direction);
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_delivery_job" + where, Long.class,
				args.toArray());
		args.add(size);
		args.add((current - 1) * size);
		List<RuntimeViews.Delivery> rows = jdbcTemplate.query("""
				SELECT id,event_id,source_event_id,direction,adapter_code,status,attempt_count,next_retry_at,last_error,
				       create_time,update_time FROM scx_ocpx_delivery_job
				""" + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
				(rs, rowNum) -> new RuntimeViews.Delivery(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
						rs.getTimestamp(8).toLocalDateTime(), rs.getString(9), rs.getTimestamp(10).toLocalDateTime(),
						rs.getTimestamp(11).toLocalDateTime()),
				args.toArray());
		Page<RuntimeViews.Delivery> page = new Page<>(current, size, total == null ? 0 : total);
		page.setRecords(rows);
		return page;
	}

	/**
	 * 分页查询 feedback stream 消费阶段产生的入口死信。
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param status 可选死信状态
	 * @return 按创建时间倒序排列的 DLQ 分页
	 */
	public Page<RuntimeViews.IngressDlq> ingressDlq(long current, long size, String status) {
		String where = StringUtils.hasText(status) ? " WHERE status=?" : "";
		List<Object> args = new java.util.ArrayList<>();
		if (StringUtils.hasText(status)) {
			args.add(status);
		}
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_ingress_dlq" + where, Long.class,
				args.toArray());
		args.add(size);
		args.add((current - 1) * size);
		List<RuntimeViews.IngressDlq> rows = jdbcTemplate.query("""
				SELECT id,partition_no,stream_record_id,event_id,reason,status,create_time,replayed_at
				FROM scx_ocpx_ingress_dlq
				""" + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
				(rs, rowNum) -> new RuntimeViews.IngressDlq(rs.getString(1), rs.getInt(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime(),
						rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toLocalDateTime()),
				args.toArray());
		Page<RuntimeViews.IngressDlq> page = new Page<>(current, size, total == null ? 0 : total);
		page.setRecords(rows);
		return page;
	}

	/**
	 * 按多个维度分页查询 OCPX 事件事实。
	 *
	 * <p>
	 * 查询以 {@code scx_ocpx_event} 为主表，并关联渠道、广告主和监测链接名称供后台展示。开始时间包含、结束时间不包含，
	 * 便于相邻时间窗口无重叠拼接。广告主条件先经过 {@link AdxAccessScope#constrainAdvertiser(String)} 收敛，防止调用方
	 * 通过修改请求参数扩大数据范围。
	 * </p>
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param eventId 可选业务事件 ID
	 * @param direction 可选来源方向
	 * @param eventType 可选归一化事件类型
	 * @param channelId 可选渠道 ID
	 * @param advertiserId 可选广告主 ID，最终受当前账号范围约束
	 * @param monitorId 可选监测链接 ID
	 * @param status 可选事件状态
	 * @param startTime 可选接入开始时间，包含边界
	 * @param endTime 可选接入结束时间，不包含边界
	 * @return 事件事实分页
	 */
	public Page<RuntimeViews.Event> events(long current, long size, String eventId, String direction, String eventType,
			String channelId, String advertiserId, String monitorId, String status, LocalDateTime startTime,
			LocalDateTime endTime) {
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> args = new ArrayList<>();
		addEquals(where, args, "e.event_id", eventId);
		addEquals(where, args, "e.source_direction", direction);
		addEquals(where, args, "e.event_type", eventType);
		addEquals(where, args, "e.channel_id", channelId);
		addEquals(where, args, "e.advertiser_id", accessScope.constrainAdvertiser(advertiserId));
		addEquals(where, args, "e.monitor_id", monitorId);
		addEquals(where, args, "e.status", status);
		if (startTime != null) {
			where.append(" AND e.received_at>=?");
			args.add(startTime);
		}
		if (endTime != null) {
			where.append(" AND e.received_at<?");
			args.add(endTime);
		}
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_event e" + where, Long.class,
				args.toArray());
		args.add(size);
		args.add((current - 1) * size);
		List<RuntimeViews.Event> rows = jdbcTemplate.query("""
				SELECT e.id,e.event_id,e.parent_event_id,e.source_direction,e.event_type,e.channel_id,c.channel_name,
				       e.advertiser_id,a.advertiser_name,e.monitor_id,m.monitor_name,e.status,e.amount,e.amount_raw,
				       e.occurred_at,e.received_at
				FROM scx_ocpx_event e
				LEFT JOIN scx_adx_channel c ON c.id=e.channel_id
				LEFT JOIN scx_adx_advertiser a ON a.id=e.advertiser_id
				LEFT JOIN scx_adx_monitor_link m ON m.id=e.monitor_id
				""" + where + " ORDER BY e.received_at DESC,e.id DESC LIMIT ? OFFSET ?",
				(rs, rowNum) -> new RuntimeViews.Event(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getBigDecimal(13),
						rs.getString(14), rs.getTimestamp(15).toLocalDateTime(), rs.getTimestamp(16).toLocalDateTime()),
				args.toArray());
		Page<RuntimeViews.Event> page = new Page<>(current, size, total == null ? 0 : total);
		page.setRecords(rows);
		return page;
	}

	/**
	 * 组装指定事件从接入到回传的完整时间线。
	 *
	 * <p>
	 * 调用方可以传事件表内部主键或业务事件 ID。服务先在当前广告主范围内解析事件身份，再以最初的 feedback 事件为根， 合并以下节点：事件事实、CPC
	 * 账务、投递任务、HTTP attempts 和扣量决策。不同来源最终按发生时间统一排序。
	 * </p>
	 *
	 * <p>
	 * 找不到事件与无权查看使用同一错误信息，避免通过管理接口探测其他广告主的数据是否存在。
	 * </p>
	 * @param id 事件内部主键或业务事件 ID
	 * @return 按时间升序排列的链路节点
	 * @throws IllegalArgumentException 事件不存在或不在当前账号数据范围内
	 */
	public List<RuntimeViews.TimelineItem> eventTimeline(String id) {
		String advertiserId = accessScope.currentAdvertiserId();
		List<EventIdentity> identities = jdbcTemplate.query("""
				SELECT event_id,parent_event_id,advertiser_id FROM scx_ocpx_event
				WHERE (id=? OR event_id=?) AND (? IS NULL OR advertiser_id=?) LIMIT 1
				""", (rs, rowNum) -> new EventIdentity(rs.getString(1), rs.getString(2), rs.getString(3)), id, id,
				advertiserId, advertiserId);
		if (identities.isEmpty()) {
			throw new IllegalArgumentException("事件不存在或无权查看");
		}
		EventIdentity identity = identities.get(0);
		String rootEventId = StringUtils.hasText(identity.parentEventId()) ? identity.parentEventId()
				: identity.eventId();
		List<RuntimeViews.TimelineItem> result = new ArrayList<>();
		result.addAll(jdbcTemplate.query(
				"""
						SELECT source_direction,status,event_id,event_type,received_at FROM scx_ocpx_event
						WHERE event_id=? OR parent_event_id=? ORDER BY received_at
						""", (rs, rowNum) -> new RuntimeViews.TimelineItem(rs.getString(1), rs.getString(2),
						rs.getString(3), rs.getString(4), rs.getTimestamp(5).toLocalDateTime()),
				rootEventId, rootEventId));
		result.addAll(jdbcTemplate.query("""
				SELECT 'CPC_BILLING',status,id,CONCAT(mode,':',COALESCE(reason,'')),create_time
				FROM scx_ocpx_billing_entry WHERE charge_event_id=?
				""", this::timelineRow, rootEventId));
		result.addAll(jdbcTemplate.query("""
				SELECT direction,status,id,CONCAT(adapter_code,':attempts=',attempt_count),create_time
				FROM scx_ocpx_delivery_job WHERE source_event_id=? OR event_id=?
				""", this::timelineRow, rootEventId, identity.eventId()));
		result.addAll(jdbcTemplate.query("""
				SELECT CONCAT(j.direction,'_ATTEMPT'),a.status,a.id,
				       CONCAT('attempt=',a.attempt_no,',http=',COALESCE(CAST(a.http_status AS CHAR),'-'),
				              ',duration_ms=',COALESCE(CAST(a.duration_ms AS CHAR),'-')),
				       a.create_time
				FROM scx_ocpx_delivery_attempt a JOIN scx_ocpx_delivery_job j ON j.id=a.job_id
				WHERE j.source_event_id=? OR j.event_id=?
				""", this::timelineRow, rootEventId, identity.eventId()));
		result.addAll(jdbcTemplate.query("""
				SELECT 'DEDUCTION',IF(forwarded=1,'FORWARDED','DEDUCTED'),id,reason,create_time
				FROM scx_ocpx_deduction_decision WHERE callback_event_id=? OR callback_event_id IN
				 (SELECT event_id FROM scx_ocpx_event WHERE parent_event_id=?)
				""", this::timelineRow, identity.eventId(), rootEventId));
		result.sort(Comparator.comparing(RuntimeViews.TimelineItem::occurredAt));
		return result;
	}

	/**
	 * 查询指定投递任务的每一次 HTTP 尝试。
	 *
	 * <p>
	 * 投递任务通过 {@code source_event_id} 关联事件事实，从而同时覆盖“feedback 上报 Coco”和“Coco callback 回传下游”
	 * 两个方向，并应用广告主范围校验。请求 query、响应 body、设备字段和 callback token 不从审计表返回，页面只能看到 预先生成的脱敏摘要。
	 * </p>
	 * @param deliveryId 投递任务 ID
	 * @return 按尝试序号升序排列的 HTTP attempts；无记录或无权查看时返回空列表
	 */
	public List<RuntimeViews.DeliveryAttempt> deliveryAttempts(String deliveryId) {
		String advertiserId = accessScope.currentAdvertiserId();
		return jdbcTemplate.query("""
				SELECT a.id,a.job_id,a.attempt_no,a.status,a.http_status,a.error_message,a.request_method,a.target_host,
				       a.target_path,a.request_summary,a.response_excerpt,a.duration_ms,a.create_time
				FROM scx_ocpx_delivery_attempt a
				JOIN scx_ocpx_delivery_job j ON j.id=a.job_id
				JOIN scx_ocpx_event e ON e.event_id=j.source_event_id
				WHERE a.job_id=? AND (? IS NULL OR e.advertiser_id=?) ORDER BY a.attempt_no
				""",
				(rs, rowNum) -> new RuntimeViews.DeliveryAttempt(rs.getString(1), rs.getString(2), rs.getInt(3),
						rs.getString(4), (Integer) rs.getObject(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getString(9), rs.getString(10), rs.getString(11), (Long) rs.getObject(12),
						rs.getTimestamp(13).toLocalDateTime()),
				deliveryId, advertiserId, advertiserId);
	}

	/**
	 * 分页查询 feedback HTTP 入口的异常与幂等审计。
	 *
	 * <p>
	 * 审计表是异步、尽力而为的运维证据，不替代 {@code scx_ocpx_event} 可靠事件事实。广告主账号查询时，通过
	 * {@code route_reference} 与监测链接 ID/业务 ID 的关联限制数据范围；时间条件同样采用左闭右开区间。
	 * </p>
	 * @param current 页码，从 1 开始
	 * @param size 每页记录数
	 * @param channelCode 可选渠道编码
	 * @param resultStatus 可选入口结果状态
	 * @param eventId 可选事件 ID
	 * @param routeReference 可选 monitor_id/task_id 路由引用
	 * @param startTime 可选开始时间，包含边界
	 * @param endTime 可选结束时间，不包含边界
	 * @return 入口审计分页
	 */
	public Page<RuntimeViews.IngressAudit> ingressAudits(long current, long size, String channelCode,
			String resultStatus, String eventId, String routeReference, LocalDateTime startTime,
			LocalDateTime endTime) {
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> args = new ArrayList<>();
		addEquals(where, args, "channel_code", channelCode);
		addEquals(where, args, "result_status", resultStatus);
		addEquals(where, args, "event_id", eventId);
		addEquals(where, args, "route_reference", routeReference);
		if (startTime != null) {
			where.append(" AND create_time>=?");
			args.add(startTime);
		}
		if (endTime != null) {
			where.append(" AND create_time<?");
			args.add(endTime);
		}
		String scopedAdvertiser = accessScope.currentAdvertiserId();
		if (StringUtils.hasText(scopedAdvertiser)) {
			where.append(" AND EXISTS (SELECT 1 FROM scx_adx_monitor_link m WHERE m.advertiser_id=?")
				.append(" AND (m.id=route_reference OR m.biz_id=route_reference))");
			args.add(scopedAdvertiser);
		}
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_ingress_audit" + where, Long.class,
				args.toArray());
		args.add(size);
		args.add((current - 1) * size);
		List<RuntimeViews.IngressAudit> rows = jdbcTemplate.query(
				"""
						SELECT id,request_id,channel_code,event_id,route_reference,result_status,http_status,reason,payload_hash,
						       client_id,key_version,remote_ip_masked,duration_ms,create_time
						FROM scx_ocpx_ingress_audit
						"""
						+ where + " ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",
				(rs, rowNum) -> new RuntimeViews.IngressAudit(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8),
						rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), (Long) rs.getObject(13),
						rs.getTimestamp(14).toLocalDateTime()),
				args.toArray());
		Page<RuntimeViews.IngressAudit> page = new Page<>(current, size, total == null ? 0 : total);
		page.setRecords(rows);
		return page;
	}

	/**
	 * 原子地把死亡投递任务恢复为待执行状态。
	 *
	 * <p>
	 * 仅 {@code DEAD} 状态会被更新；同时重置尝试次数、租约、最后错误和下次执行时间。该方法只恢复任务状态， 不在管理请求线程中直接发起外部 HTTP
	 * 请求，实际发送仍由投递 worker 完成。
	 * </p>
	 * @param id 投递任务 ID
	 * @throws IllegalArgumentException 任务不存在或当前不是 DEAD 状态
	 */
	@Transactional
	public void replayDelivery(String id) {
		int updated = jdbcTemplate.update("""
				UPDATE scx_ocpx_delivery_job SET status='PENDING',attempt_count=0,next_retry_at=NOW(3),
				 lease_owner=NULL,lease_until=NULL,last_error=NULL,update_time=NOW(3)
				WHERE id=? AND status='DEAD'
				""", id);
		if (updated == 0)
			throw new IllegalArgumentException("仅死亡任务可以重放");
	}

	/**
	 * 将可恢复的入口死信重新写入原 feedback 分区 stream。
	 *
	 * <p>
	 * 为避免把确定无效的数据反复投入消费队列，{@code INVALID_ENVELOPE} 类型必须由接入方修复后重新上报。其他待处理死信 先写入 Redis
	 * stream，成功后再把数据库记录标记为 {@code REPLAYED}；若 Redis 写入失败，数据库状态保持 PENDING， 便于稍后再次操作。
	 * </p>
	 * @param id 入口 DLQ 记录 ID
	 * @throws IllegalArgumentException 记录不存在、状态不可重放或协议 envelope 无效
	 */
	public void replayIngressDlq(String id) {
		List<IngressDlqPayload> rows = jdbcTemplate.query("""
				SELECT partition_no,event_id,envelope,reason FROM scx_ocpx_ingress_dlq
				WHERE id=? AND status='PENDING'
				""",
				(rs, rowNum) -> new IngressDlqPayload(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)),
				id);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("仅待处理入口死信可以重放");
		}
		IngressDlqPayload payload = rows.get(0);
		if (payload.reason().startsWith("INVALID_ENVELOPE")) {
			throw new IllegalArgumentException("无效协议死信必须修正数据后重新接入，不能直接重放");
		}
		String stream = "scx:ocpx:{feedback:" + payload.partition() + "}:stream";
		redisTemplate.opsForStream()
			.add(stream, Map.of("event_id", payload.eventId() == null ? "" : payload.eventId(), "envelope",
					payload.envelope()));
		jdbcTemplate.update(
				"UPDATE scx_ocpx_ingress_dlq SET status='REPLAYED',replayed_at=NOW(3) WHERE id=? AND status='PENDING'",
				id);
	}

	/**
	 * 汇总所有 feedback stream 的当前长度。
	 * @return 各分区长度总和；Redis 不可访问时返回 -1 表示未知
	 */
	private long feedbackPending() {
		long pending = 0;
		try {
			for (int partition = 0; partition < partitionCount; partition++) {
				String key = "scx:ocpx:{feedback:" + partition + "}:stream";
				Long size = redisTemplate.opsForStream().size(key);
				pending += size == null ? 0 : size;
			}
		}
		catch (Exception ignored) {
			return -1;
		}
		return pending;
	}

	/** 将不同 SQL 查询统一映射为时间线节点。 */
	private RuntimeViews.TimelineItem timelineRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new RuntimeViews.TimelineItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getTimestamp(5).toLocalDateTime());
	}

	/**
	 * 为可选的精确匹配条件追加 SQL 片段和绑定参数。
	 *
	 * <p>
	 * {@code column} 只能由本类内部传入固定列名；外部输入仅进入 {@code args}，从而保持预编译参数绑定。
	 * </p>
	 */
	private void addEquals(StringBuilder where, List<Object> args, String column, String value) {
		if (StringUtils.hasText(value)) {
			where.append(" AND ").append(column).append("=?");
			args.add(value);
		}
	}

	/** 入口死信重放所需的最小内部投影。 */
	private record IngressDlqPayload(int partition, String eventId, String envelope, String reason) {
	}

	/** 事件时间线根节点解析所需的最小内部投影。 */
	private record EventIdentity(String eventId, String parentEventId, String advertiserId) {
	}

}
