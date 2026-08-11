package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.config.OcpxRuntimeCenterProperties;
import com.yzytech.scx.ocpx.admin.dto.RuntimeRequests;
import com.yzytech.scx.ocpx.admin.dto.RuntimeViews;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * S10 management projection over append-only runtime facts and failures.
 *
 * <p>
 * Every query applies the same Advertiser, Channel and MediaAccount scope intersection.
 * Values used for display are frozen identifiers or sanitized fingerprints; this service
 * never decrypts a route snapshot or raw envelope.
 * </p>
 */
@Service
public class OcpxRuntimeService {

	private static final String PREVIEW_PURPOSE = "S10_RUNTIME_REPLAY_PREVIEW";

	private static final Set<String> FACT_TYPES = Set.of("INTEGRATION", "CLICK", "CONVERSION", "DELIVERY_ATTEMPT",
			"CONFIG_APPLY", "REPLAY_RESULT");

	private static final Set<String> DIRECTIONS = Set.of("INGRESS", "UPSTREAM", "DOWNSTREAM", "INTERNAL");

	private static final Set<String> PROCESSING_STATUSES = Set.of("PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "DLQ",
			"ISOLATED");

	private static final Set<String> FAILURE_CLASSES = Set.of("RETRYABLE", "NON_RETRYABLE", "PROTOCOL_REJECTED",
			"SIGNATURE_FAILED", "SCOPE_REJECTED", "DUPLICATE", "LATE_ARRIVAL", "UNKNOWN");

	private final JdbcTemplate jdbcTemplate;

	private final OcpxAccessScope accessScope;

	private final ProtocolCrypto protocolCrypto;

	private final OcpxRuntimeCenterProperties properties;

	public OcpxRuntimeService(JdbcTemplate jdbcTemplate, OcpxAccessScope accessScope, ProtocolCrypto protocolCrypto,
			OcpxRuntimeCenterProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.accessScope = accessScope;
		this.protocolCrypto = protocolCrypto;
		this.properties = properties;
	}

	public RuntimeViews.Overview overview() {
		ScopeSql factScope = scope("f", OcpxAccessScope.PermissionLevel.VIEW);
		Map<String, Long> statuses = grouped(
				"SELECT f.processing_status,COUNT(*) FROM scx_ocpx_runtime_fact f WHERE 1=1", factScope,
				" GROUP BY f.processing_status");
		Map<String, Long> factTypes = grouped("SELECT f.fact_type,COUNT(*) FROM scx_ocpx_runtime_fact f WHERE 1=1",
				factScope, " GROUP BY f.fact_type");
		ScopeSql failureScope = scope("d", OcpxAccessScope.PermissionLevel.VIEW);
		Map<String, Long> failures = grouped("SELECT d.failure_class,COUNT(*) FROM scx_ocpx_runtime_failure d WHERE 1=1",
				failureScope, " GROUP BY d.failure_class");
		ScopeSql replayScope = scope("r", OcpxAccessScope.PermissionLevel.VIEW);
		Map<String, Long> replay = grouped("SELECT r.status,COUNT(*) FROM scx_ocpx_runtime_replay_request r WHERE 1=1",
				replayScope, " GROUP BY r.status");
		return new RuntimeViews.Overview(statuses.values().stream().mapToLong(Long::longValue).sum(),
				statuses.getOrDefault("SUCCEEDED", 0L), statuses.getOrDefault("FAILED", 0L),
				statuses.getOrDefault("DLQ", 0L), replay.getOrDefault("QUEUED", 0L),
				replay.getOrDefault("TERMINAL", 0L), workerStatus(), LocalDateTime.now(), freshness(statuses),
				traceId(), Map.copyOf(factTypes), Map.copyOf(failures));
	}

	public RuntimeViews.PageResult<RuntimeViews.Fact> facts(RuntimeRequests.Query requested) {
		NormalizedQuery query = normalize(requested, null);
		ScopeSql scope = scope("f", OcpxAccessScope.PermissionLevel.VIEW);
		StringBuilder where = new StringBuilder(" WHERE f.received_time>=? AND f.received_time<?");
		List<Object> args = new ArrayList<>(List.of(query.startTime(), query.endTime()));
		addEquals(where, args, "f.fact_type", query.factType());
		addEquals(where, args, "f.direction", query.direction());
		addEquals(where, args, "f.processing_status", query.status());
		addEquals(where, args, "f.advertiser_id", query.advertiserId());
		addEquals(where, args, "f.product_id", query.productId());
		addEquals(where, args, "f.app_link_id", query.appLinkId());
		addEquals(where, args, "f.task_id", query.taskId());
		addEquals(where, args, "f.channel_id", query.channelId());
		addEquals(where, args, "f.media_account_id", query.mediaAccountId());
		addEquals(where, args, "f.route_version_id", query.routeVersionId());
		addEquals(where, args, "f.event_code", query.eventCode());
		addEquals(where, args, "f.trace_id", query.traceId());
		if (StringUtils.hasText(query.exactId())) {
			where.append(
					" AND (f.id=? OR f.request_id_hash=? OR f.correlation_id_hash=? OR f.external_click_id_hash=? OR f.media_task_id_hash=?)");
			args.add(query.exactId());
			String exactHash = protocolCrypto.blindIndex("S10_RUNTIME_EXACT", query.exactId());
			args.addAll(Collections.nCopies(4, exactHash));
		}
		where.append(scope.clause());
		args.addAll(scope.args());
		long total = count("SELECT COUNT(*) FROM scx_ocpx_runtime_fact f" + where, args);
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(query.size());
		pageArgs.add((query.current() - 1) * query.size());
		List<RuntimeViews.Fact> records = jdbcTemplate.query("""
				SELECT f.id,f.fact_type,f.direction,f.acceptance_status,f.processing_status,
				 f.advertiser_id,f.product_id,f.app_link_id,f.task_id,f.channel_id,f.media_account_id,
				 f.operator_user_id,f.route_id,f.route_version_id,f.route_version,f.event_code,f.trace_id,
				 f.request_id_hash,f.correlation_id_hash,f.external_click_id_hash,f.media_task_id_hash,
				 f.summary_code,f.dimension_status,f.quality_status,f.event_time,f.received_time
				FROM scx_ocpx_runtime_fact f
				""" + where + query.orderBy() + " LIMIT ? OFFSET ?", this::factRow, pageArgs.toArray());
		return page(records, total, query, quality(records.stream().map(RuntimeViews.Fact::dimensionStatus).toList()));
	}

	public RuntimeViews.PageResult<RuntimeViews.Fact> taskFacts(String taskId, String factType,
			RuntimeRequests.Query requested) {
		requireTask(taskId, OcpxAccessScope.PermissionLevel.VIEW);
		RuntimeRequests.Query fixed = new RuntimeRequests.Query(requested.current(), requested.size(),
				requested.startTime(), requested.endTime(), factType, requested.direction(), requested.status(),
				requested.failureClass(), requested.advertiserId(), requested.productId(), requested.appLinkId(),
				taskId, requested.channelId(), requested.mediaAccountId(), requested.routeVersionId(),
				requested.eventCode(), requested.exactId(), requested.traceId(), requested.sort());
		return facts(fixed);
	}

	public RuntimeViews.PageResult<RuntimeViews.Failure> failures(RuntimeRequests.Query requested) {
		NormalizedQuery query = normalize(requested, null);
		ScopeSql scope = scope("d", OcpxAccessScope.PermissionLevel.VIEW);
		StringBuilder where = new StringBuilder(" WHERE d.received_time>=? AND d.received_time<?");
		List<Object> args = new ArrayList<>(List.of(query.startTime(), query.endTime()));
		addEquals(where, args, "d.failure_class", query.failureClass());
		addEquals(where, args, "d.advertiser_id", query.advertiserId());
		addEquals(where, args, "d.product_id", query.productId());
		addEquals(where, args, "d.app_link_id", query.appLinkId());
		addEquals(where, args, "d.task_id", query.taskId());
		addEquals(where, args, "d.channel_id", query.channelId());
		addEquals(where, args, "d.media_account_id", query.mediaAccountId());
		addEquals(where, args, "d.route_version_id", query.routeVersionId());
		addEquals(where, args, "d.trace_id", query.traceId());
		if (StringUtils.hasText(query.exactId())) {
			where.append(" AND (d.id=? OR d.source_id=? OR d.idempotency_key_hash=? OR d.correlation_id_hash=?)");
			args.add(query.exactId());
			args.add(query.exactId());
			String exactHash = protocolCrypto.blindIndex("S10_RUNTIME_EXACT", query.exactId());
			args.add(exactHash);
			args.add(exactHash);
		}
		where.append(scope.clause());
		args.addAll(scope.args());
		long total = count("SELECT COUNT(*) FROM scx_ocpx_runtime_failure d" + where, args);
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(query.size());
		pageArgs.add((query.current() - 1) * query.size());
		List<RuntimeViews.Failure> records = jdbcTemplate.query(
				"""
						SELECT d.id,d.source_fact_id,d.source_type,d.failure_class,d.error_code,d.replayable,d.failure_revision,
						 d.task_id,d.advertiser_id,d.channel_id,d.media_account_id,d.route_id,d.route_version_id,d.route_version,
						 d.trace_id,d.summary_hash,d.dimension_status,d.event_time,d.received_time
						FROM scx_ocpx_runtime_failure d
						"""
						+ where + query.orderBy().replace("f.", "d.") + " LIMIT ? OFFSET ?",
				this::failureRow, pageArgs.toArray());
		return page(records, total, query,
				quality(records.stream().map(RuntimeViews.Failure::dimensionStatus).toList()));
	}

	public RuntimeViews.Timeline execution(String id) {
		ScopeSql factScope = scope("f", OcpxAccessScope.PermissionLevel.VIEW);
		List<RuntimeViews.Fact> facts = jdbcTemplate.query("""
				SELECT f.id,f.fact_type,f.direction,f.acceptance_status,f.processing_status,
				 f.advertiser_id,f.product_id,f.app_link_id,f.task_id,f.channel_id,f.media_account_id,
				 f.operator_user_id,f.route_id,f.route_version_id,f.route_version,f.event_code,f.trace_id,
				 f.request_id_hash,f.correlation_id_hash,f.external_click_id_hash,f.media_task_id_hash,
				 f.summary_code,f.dimension_status,f.quality_status,f.event_time,f.received_time
				FROM scx_ocpx_runtime_fact f WHERE f.id=?
				""" + factScope.clause() + " LIMIT 1", this::factRow, concat(List.of(id), factScope.args()).toArray());
		ScopeSql failureScope = scope("d", OcpxAccessScope.PermissionLevel.VIEW);
		List<RuntimeViews.Failure> failures = jdbcTemplate.query(
				"""
						SELECT d.id,d.source_fact_id,d.source_type,d.failure_class,d.error_code,d.replayable,d.failure_revision,
						 d.task_id,d.advertiser_id,d.channel_id,d.media_account_id,d.route_id,d.route_version_id,d.route_version,
						 d.trace_id,d.summary_hash,d.dimension_status,d.event_time,d.received_time
						FROM scx_ocpx_runtime_failure d WHERE (d.id=? OR d.source_fact_id=?)
						"""
						+ failureScope.clause() + " ORDER BY d.received_time DESC LIMIT 1",
				this::failureRow, concat(List.of(id, id), failureScope.args()).toArray());
		if (facts.isEmpty() && failures.isEmpty()) {
			throw forbidden();
		}
		String failureId = failures.isEmpty() ? null : failures.get(0).id();
		List<RuntimeViews.ReplayStatus> replays = failureId == null ? List.of() : replayStatuses(failureId);
		List<RuntimeViews.Attempt> attempts = failureId == null ? List.of() : replayAttempts(failureId);
		return new RuntimeViews.Timeline(id, facts.isEmpty() ? null : facts.get(0),
				failures.isEmpty() ? null : failures.get(0), attempts, replays, traceId());
	}

	public RuntimeViews.ReplayPreview replayPreview(RuntimeRequests.ReplayPreview request) {
		FailureContext failure = requireFailure(request.failureId(), OcpxAccessScope.PermissionLevel.OPERATE);
		List<String> blockers = replayBlockers(failure, request.expectedFailureRevision());
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(properties.getPreviewTtlSeconds());
		String payload = String.join("|", failure.id(), Long.toString(request.expectedFailureRevision()),
				failure.routeVersionId(), Long.toString(failure.routeVersion()),
				Long.toString(toEpochMillis(expiresAt)), SecurityUtils.getUserId());
		String token = protocolCrypto.signToken(PREVIEW_PURPOSE, payload);
		audit("REPLAY", null, failure.id(), failure.taskId(), failure.routeVersionId(), "PREVIEW",
				blockers.isEmpty() ? "PASS" : "FAIL", null);
		return new RuntimeViews.ReplayPreview(failure.id(), failure.revision(), failure.taskId(),
				failure.routeVersionId(), failure.routeVersion(), failure.failureClass(), failure.errorCode(),
				failure.replayable(), "ORIGINAL_SNAPSHOT_ONLY", "异步处理器可能产生外发；HTTP 预览不外发", "沿用原事实幂等键；不得重复计费",
				workerStatus(), expiresAt, token, traceId(), blockers);
	}

	@Transactional
	public RuntimeViews.ReplayStatus replay(RuntimeRequests.ReplayConfirm request) {
		cn.dev33.satoken.stp.StpUtil.checkSafe();
		FailureContext failure = requireFailure(request.failureId(), OcpxAccessScope.PermissionLevel.OPERATE);
		verifyPreviewToken(request, failure);
		List<String> blockers = replayBlockers(failure, request.expectedFailureRevision());
		if (!blockers.isEmpty()) {
			audit("REPLAY", null, failure.id(), failure.taskId(), failure.routeVersionId(), "REJECT", "FAIL",
					request.reason());
			throw conflict(blockers.get(0));
		}
		List<RuntimeViews.ReplayStatus> existing = replayByRequestId(request.requestId());
		String fingerprint = hash(
				String.join("|", failure.id(), Long.toString(failure.revision()), "ORIGINAL_SNAPSHOT_ONLY"));
		if (!existing.isEmpty()) {
			RuntimeViews.ReplayStatus prior = existing.get(0);
			if (!prior.failureId().equals(failure.id())) {
				throw conflict("requestId 已用于其他补偿命令");
			}
			return prior;
		}
		String commandId = id();
		try {
			jdbcTemplate.update(
					"""
							INSERT INTO scx_ocpx_runtime_replay_request
							(id,request_id,source_failure_id,expected_failure_revision,source_route_version_id,source_route_version,
							 command_type,mode,tenant_id,advertiser_id,product_id,app_link_id,task_id,channel_id,media_account_id,
							 operator_user_id,requested_by,reason,ticket,command_fingerprint,status,attempt_count,max_attempts,
							 next_attempt_at,create_time,update_time)
							VALUES (?,?,?,?,?,?,'REPLAY','ORIGINAL_SNAPSHOT_ONLY',?,?,?,?,?,?,?,?,?,?,?,?,?,'QUEUED',0,?,NOW(3),NOW(3),NOW(3))
							""",
					commandId, request.requestId(), failure.id(), failure.revision(), failure.routeVersionId(),
					failure.routeVersion(), failure.tenantId(), failure.advertiserId(), failure.productId(),
					failure.appLinkId(), failure.taskId(), failure.channelId(), failure.mediaAccountId(),
					failure.operatorUserId(), SecurityUtils.getUserId(), request.reason(),
					blankToNull(request.ticket()), fingerprint, properties.getMaxReplayAttempts());
		}
		catch (org.springframework.dao.DuplicateKeyException duplicate) {
			List<RuntimeViews.ReplayStatus> raced = replayByRequestId(request.requestId());
			if (!raced.isEmpty() && raced.get(0).failureId().equals(failure.id())) {
				return raced.get(0);
			}
			throw conflict("该失败事实已有补偿命令或 requestId 冲突");
		}
		audit("REPLAY", commandId, failure.id(), failure.taskId(), failure.routeVersionId(), "CONFIRM", "QUEUED",
				request.reason());
		return replayByRequestId(request.requestId()).get(0);
	}

	public RuntimeViews.ReplayStatus replayStatus(String id) {
		ScopeSql scope = scope("r", OcpxAccessScope.PermissionLevel.VIEW);
		List<RuntimeViews.ReplayStatus> rows = jdbcTemplate.query(
				replaySelect() + " WHERE (r.id=? OR r.request_id=?)" + scope.clause() + " LIMIT 1", this::replayRow,
				concat(List.of(id, id), scope.args()).toArray());
		if (rows.isEmpty()) {
			throw forbidden();
		}
		return rows.get(0);
	}

	private void verifyPreviewToken(RuntimeRequests.ReplayConfirm request, FailureContext failure) {
		try {
			String decoded = protocolCrypto.verifyToken(PREVIEW_PURPOSE, request.previewToken());
			String[] parts = decoded.split("\\|", -1);
			if (parts.length != 6 || !parts[0].equals(failure.id())
					|| Long.parseLong(parts[1]) != request.expectedFailureRevision()
					|| !parts[2].equals(failure.routeVersionId()) || Long.parseLong(parts[3]) != failure.routeVersion()
					|| LocalDateTime.now().isAfter(fromEpochMillis(Long.parseLong(parts[4])))
					|| !parts[5].equals(SecurityUtils.getUserId())) {
				throw new IllegalArgumentException("补偿预览已过期或不匹配");
			}
		}
		catch (RuntimeException error) {
			throw conflict("补偿预览已过期或不匹配，请重新预览");
		}
	}

	private List<String> replayBlockers(FailureContext failure, long expectedRevision) {
		List<String> blockers = new ArrayList<>();
		if (failure.revision() != expectedRevision) {
			blockers.add("失败事实 revision 已变化，请刷新");
		}
		if (!failure.replayable() || !"RETRYABLE".equals(failure.failureClass())) {
			blockers.add("该失败分类不可补偿");
		}
		if (!"FROZEN".equals(failure.dimensionStatus()) || !StringUtils.hasText(failure.routeVersionId())) {
			blockers.add("缺少原冻结路由版本，禁止猜测当前配置");
		}
		Long route = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM scx_ocpx_route_version v WHERE v.id=? AND v.route_version=? AND v.status='PUBLISHED'",
				Long.class, failure.routeVersionId(), failure.routeVersion());
		if (route == null || route == 0) {
			blockers.add("原冻结路由快照不存在");
		}
		Long denied = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_route_runtime_state s
				WHERE s.route_version_id=? AND (s.acceptance_status='DENIED' OR s.deny_at IS NOT NULL)
				""", Long.class, failure.routeVersionId());
		if (denied != null && denied > 0) {
			blockers.add("路由已停流，禁止创建新的补偿执行");
		}
		Long prior = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_runtime_replay_request
				WHERE source_failure_id=? AND status IN ('QUEUED','CLAIMED','RETRY_WAIT','SUCCEEDED')
				""", Long.class, failure.id());
		if (prior != null && prior > 0) {
			blockers.add("该失败事实已有进行中或成功的补偿命令");
		}
		return List.copyOf(blockers);
	}

	private FailureContext requireFailure(String id, OcpxAccessScope.PermissionLevel level) {
		ScopeSql scope = scope("d", level);
		List<FailureContext> rows = jdbcTemplate.query("""
				SELECT d.id,d.failure_revision,d.failure_class,d.error_code,d.replayable,d.tenant_id,d.advertiser_id,
				 d.product_id,d.app_link_id,d.task_id,d.channel_id,d.media_account_id,d.operator_user_id,
				 d.route_id,d.route_version_id,d.route_version,d.event_snapshot_schema_version,d.dimension_status
				FROM scx_ocpx_runtime_failure d WHERE d.id=?
				""" + scope.clause() + " LIMIT 1",
				(rs, rowNum) -> new FailureContext(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
						rs.getBoolean(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
						rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
						rs.getString(15), rs.getLong(16), (Integer) rs.getObject(17), rs.getString(18)),
				concat(List.of(id), scope.args()).toArray());
		if (rows.isEmpty()) {
			throw forbidden();
		}
		return rows.get(0);
	}

	private void requireTask(String taskId, OcpxAccessScope.PermissionLevel level) {
		List<TaskScope> rows = jdbcTemplate.query("""
				SELECT advertiser_id,channel_id,media_account_id FROM scx_ocpx_task
				WHERE id=? AND deleted=0 LIMIT 1
				""", (rs, rowNum) -> new TaskScope(rs.getString(1), rs.getString(2), rs.getString(3)), taskId);
		if (rows.isEmpty()) {
			throw forbidden();
		}
		TaskScope row = rows.get(0);
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, row.advertiserId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, row.channelId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, row.mediaAccountId(), level);
	}

	private NormalizedQuery normalize(RuntimeRequests.Query requested, String forcedFactType) {
		long current = requested.current() == null ? 1 : requested.current();
		long size = requested.size() == null ? properties.getDefaultPageSize() : requested.size();
		if (current < 1 || size < 1 || size > properties.getMaxPageSize()) {
			throw new IllegalArgumentException("分页参数超出允许范围");
		}
		LocalDateTime end = requested.endTime() == null ? LocalDateTime.now() : requested.endTime();
		LocalDateTime start = requested.startTime() == null ? end.minusHours(properties.getDefaultSpanHours())
				: requested.startTime();
		if (!start.isBefore(end) || Duration.between(start, end).toHours() > properties.getMaxSpanHours()) {
			throw new IllegalArgumentException("时间范围必须为左闭右开且不超过 " + properties.getMaxSpanHours() + " 小时");
		}
		String factType = forcedFactType == null ? upper(requested.factType()) : forcedFactType;
		validate(factType, FACT_TYPES, "factType");
		String direction = upper(requested.direction());
		validate(direction, DIRECTIONS, "direction");
		String status = upper(requested.status());
		validate(status, PROCESSING_STATUSES, "status");
		String failureClass = upper(requested.failureClass());
		validate(failureClass, FAILURE_CLASSES, "failureClass");
		String sort = upper(requested.sort());
		if (sort != null && !Set.of("RECEIVED_ASC", "RECEIVED_DESC").contains(sort)) {
			throw new IllegalArgumentException("sort 不在白名单");
		}
		validateRequestedScope(requested.advertiserId(), requested.channelId(), requested.mediaAccountId());
		return new NormalizedQuery(current, size, start, end, factType, direction, status, failureClass,
				requested.advertiserId(), requested.productId(), requested.appLinkId(), requested.taskId(),
				requested.channelId(), requested.mediaAccountId(), requested.routeVersionId(), requested.eventCode(),
				requested.exactId(), requested.traceId(),
				"RECEIVED_ASC".equals(sort) ? " ORDER BY f.received_time ASC,f.sequence_no ASC"
						: " ORDER BY f.received_time DESC,f.sequence_no DESC");
	}

	private void validateRequestedScope(String advertiserId, String channelId, String mediaAccountId) {
		if (StringUtils.hasText(advertiserId)) {
			accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, advertiserId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(channelId)) {
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (StringUtils.hasText(mediaAccountId)) {
			accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, mediaAccountId,
					OcpxAccessScope.PermissionLevel.VIEW);
		}
	}

	private ScopeSql scope(String alias, OcpxAccessScope.PermissionLevel level) {
		OcpxAccessScope.Snapshot snapshot = accessScope.current();
		StringBuilder clause = new StringBuilder();
		List<Object> args = new ArrayList<>();
		appendRestriction(clause, args, alias + ".advertiser_id",
				snapshot.restriction(OcpxAccessScope.ScopeType.ADVERTISER, level));
		appendRestriction(clause, args, alias + ".channel_id",
				snapshot.restriction(OcpxAccessScope.ScopeType.CHANNEL, level));
		appendRestriction(clause, args, alias + ".media_account_id",
				snapshot.restriction(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, level));
		return new ScopeSql(clause.toString(), List.copyOf(args));
	}

	private void appendRestriction(StringBuilder clause, List<Object> args, String column,
			OcpxAccessScope.Restriction restriction) {
		if (restriction.unrestricted()) {
			return;
		}
		if (restriction.ids().isEmpty()) {
			clause.append(" AND 1=0");
			return;
		}
		clause.append(" AND ")
			.append(column)
			.append(" IN (")
			.append(String.join(",", Collections.nCopies(restriction.ids().size(), "?")))
			.append(")");
		args.addAll(restriction.ids());
	}

	private RuntimeViews.Fact factRow(ResultSet rs, int rowNum) throws SQLException {
		return new RuntimeViews.Fact(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
				rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), (Long) rs.getObject(15),
				rs.getString(16), rs.getString(17), fingerprint(rs.getString(18)), fingerprint(rs.getString(19)),
				fingerprint(rs.getString(20)), fingerprint(rs.getString(21)), rs.getString(22), rs.getString(23),
				rs.getString(24), timestamp(rs, 25), timestamp(rs, 26), false);
	}

	private RuntimeViews.Failure failureRow(ResultSet rs, int rowNum) throws SQLException {
		return new RuntimeViews.Failure(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getBoolean(6), rs.getLong(7), rs.getString(8), rs.getString(9), rs.getString(10),
				rs.getString(11), rs.getString(12), rs.getString(13), (Long) rs.getObject(14), rs.getString(15),
				fingerprint(rs.getString(16)), rs.getString(17), timestamp(rs, 18), timestamp(rs, 19), false);
	}

	private RuntimeViews.ReplayStatus replayRow(ResultSet rs, int rowNum) throws SQLException {
		return new RuntimeViews.ReplayStatus(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getInt(9), rs.getInt(10),
				rs.getString(11), rs.getString(12), timestamp(rs, 13), timestamp(rs, 14), timestamp(rs, 15),
				timestamp(rs, 16), workerStatus());
	}

	private String replaySelect() {
		return """
				SELECT r.request_id,r.id,r.source_failure_id,r.task_id,r.source_route_version_id,r.source_route_version,
				 r.mode,r.status,r.attempt_count,r.max_attempts,r.last_error_class,r.last_error_code,r.next_attempt_at,
				 r.lease_until,r.create_time,r.completed_at FROM scx_ocpx_runtime_replay_request r
				""";
	}

	private List<RuntimeViews.ReplayStatus> replayByRequestId(String requestId) {
		ScopeSql scope = scope("r", OcpxAccessScope.PermissionLevel.VIEW);
		return jdbcTemplate.query(replaySelect() + " WHERE r.request_id=?" + scope.clause() + " LIMIT 1",
				this::replayRow, concat(List.of(requestId), scope.args()).toArray());
	}

	private List<RuntimeViews.ReplayStatus> replayStatuses(String failureId) {
		ScopeSql scope = scope("r", OcpxAccessScope.PermissionLevel.VIEW);
		return jdbcTemplate.query(
				replaySelect() + " WHERE r.source_failure_id=?" + scope.clause() + " ORDER BY r.create_time",
				this::replayRow, concat(List.of(failureId), scope.args()).toArray());
	}

	private List<RuntimeViews.Attempt> replayAttempts(String failureId) {
		ScopeSql scope = scope("r", OcpxAccessScope.PermissionLevel.VIEW);
		return jdbcTemplate.query("""
				SELECT a.id,a.attempt_no,a.status,a.error_class,a.error_code,a.result_summary_hash,
				 a.started_at,a.finished_at,a.next_retry_at
				FROM scx_ocpx_runtime_replay_attempt a
				JOIN scx_ocpx_runtime_replay_request r ON r.id=a.replay_request_id
				WHERE r.source_failure_id=?
				""" + scope.clause() + " ORDER BY a.attempt_no",
				(rs, rowNum) -> new RuntimeViews.Attempt(rs.getString(1), rs.getInt(2), rs.getString(3),
						rs.getString(4), rs.getString(5), fingerprint(rs.getString(6)), timestamp(rs, 7),
						timestamp(rs, 8), timestamp(rs, 9)),
				concat(List.of(failureId), scope.args()).toArray());
	}

	private Map<String, Long> grouped(String select, ScopeSql scope, String groupBy) {
		Map<String, Long> result = new LinkedHashMap<>();
		jdbcTemplate.query(select + scope.clause() + groupBy, scope.args().toArray(), (ResultSet rs) -> {
			result.put(rs.getString(1), rs.getLong(2));
		});
		return result;
	}

	private long count(String sql, List<Object> args) {
		Long value = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
		return value == null ? 0 : value;
	}

	private <T> RuntimeViews.PageResult<T> page(List<T> records, long total, NormalizedQuery query,
			List<String> qualityFlags) {
		return new RuntimeViews.PageResult<>(List.copyOf(records), total, query.current(), query.size(),
				LocalDateTime.now(), qualityFlags.contains("UNMAPPED") ? "PROVISIONAL" : "FINAL", traceId(),
				qualityFlags);
	}

	private List<String> quality(List<String> dimensions) {
		return dimensions.stream().anyMatch("UNMAPPED"::equals) ? List.of("UNMAPPED") : List.of();
	}

	private String freshness(Map<String, Long> statuses) {
		return statuses.getOrDefault("PENDING", 0L) + statuses.getOrDefault("PROCESSING", 0L) > 0 ? "PROVISIONAL"
				: "FINAL";
	}

	private String workerStatus() {
		return properties.isReplayWorkerEnabled() ? "ENABLED" : "DISABLED";
	}

	private void audit(String commandType, String commandId, String failureId, String taskId, String routeVersionId,
			String action, String outcome, String reason) {
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_runtime_command_audit
				(id,command_type,command_id,source_failure_id,task_id,route_version_id,action,outcome,actor,
				 reason_hash,trace_id,create_time)
				VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW(3))
				""", id(), commandType, commandId, failureId, taskId, routeVersionId, action, outcome,
				SecurityUtils.getUserId(), reason == null ? null : hash(reason), traceId());
	}

	private void addEquals(StringBuilder where, List<Object> args, String column, String value) {
		if (StringUtils.hasText(value)) {
			where.append(" AND ").append(column).append("=?");
			args.add(value);
		}
	}

	private void validate(String value, Set<String> allowed, String field) {
		if (value != null && !allowed.contains(value)) {
			throw new IllegalArgumentException(field + " 不在白名单");
		}
	}

	private String upper(String value) {
		return StringUtils.hasText(value) ? value.trim().toUpperCase(java.util.Locale.ROOT) : null;
	}

	private LocalDateTime timestamp(ResultSet rs, int column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toLocalDateTime();
	}

	private String fingerprint(String value) {
		return StringUtils.hasText(value) ? value.substring(0, Math.min(value.length(), 10)) + "…" : null;
	}

	private String blankToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private String hash(String value) {
		try {
			return java.util.HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("Cannot calculate management fingerprint", error);
		}
	}

	private long toEpochMillis(LocalDateTime value) {
		return value.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
	}

	private LocalDateTime fromEpochMillis(long value) {
		return java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
	}

	private String traceId() {
		return UUID.randomUUID().toString();
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private List<Object> concat(List<?> prefix, List<Object> suffix) {
		List<Object> result = new ArrayList<>(prefix);
		result.addAll(suffix);
		return result;
	}

	private BootBusinessException forbidden() {
		return new BootBusinessException(HttpStatus.FORBIDDEN.value(), "对象不存在或无权访问");
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	private record ScopeSql(String clause, List<Object> args) {
	}

	private record TaskScope(String advertiserId, String channelId, String mediaAccountId) {
	}

	private record FailureContext(String id, long revision, String failureClass, String errorCode, boolean replayable,
			String tenantId, String advertiserId, String productId, String appLinkId, String taskId, String channelId,
			String mediaAccountId, String operatorUserId, String routeId, String routeVersionId, long routeVersion,
			Integer eventSnapshotSchemaVersion, String dimensionStatus) {
	}

	private record NormalizedQuery(long current, long size, LocalDateTime startTime, LocalDateTime endTime,
			String factType, String direction, String status, String failureClass, String advertiserId,
			String productId, String appLinkId, String taskId, String channelId, String mediaAccountId,
			String routeVersionId, String eventCode, String exactId, String traceId, String orderBy) {
	}

}
