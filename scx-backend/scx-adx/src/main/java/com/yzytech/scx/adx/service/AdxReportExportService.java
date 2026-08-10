package com.yzytech.scx.adx.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.adx.config.AdxReportingProperties;
import com.yzytech.scx.adx.dto.UnifiedReportQuery;
import com.yzytech.scx.adx.dto.UnifiedReportViews;
import com.yzytech.scx.adx.service.AdxAccessScope.Restriction;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.system.api.dto.ManagedStorageObject;
import com.yzytech.scx.system.api.service.StorageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdxReportExportService {

	private static final String STANDARD = "STANDARD";

	private static final String DEDUCTION = "DEDUCTION";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final AdxAccessScope accessScope;

	private final AdxUnifiedReportService reportService;

	private final AdxReportingProperties properties;

	private final StorageUploadService storageUploadService;

	private final TransactionTemplate transactionTemplate;

	public UnifiedReportViews.ExportTask createStandard(UnifiedReportQuery query) {
		return create(STANDARD, query, StpUtil.hasPermission("adx:report:metric:sensitive"));
	}

	public UnifiedReportViews.ExportTask createDeduction(UnifiedReportQuery query) {
		return create(DEDUCTION, query, true);
	}

	private UnifiedReportViews.ExportTask create(String type, UnifiedReportQuery query, boolean sensitive) {
		validateExportRange(query);
		String userId = SecurityUtils.getUserId();
		AdxUnifiedReportService.FrozenScope scope = reportService.currentScope();
		ExportEnvelope envelope = new ExportEnvelope(query, sensitive);
		String queryJson = json(envelope);
		String scopeJson = json(scope);
		String queryHash = sha256(queryJson);
		String scopeHash = sha256(scopeJson);
		LocalDateTime bucket = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
		String id = id();
		jdbcTemplate.getJdbcTemplate().update("""
				INSERT IGNORE INTO scx_adx_report_export_task
				(id,user_id,export_type,query_json,query_hash,scope_json,scope_hash,idempotency_bucket,
				 metric_definitions_version,status,attempt_count,next_attempt_at,create_time,update_time)
				VALUES (?,?,?,?,?,?,?,?,'S8_V1','PENDING',0,NOW(3),NOW(3),NOW(3))
				""", id, userId, type, queryJson, queryHash, scopeJson, scopeHash, bucket);
		ExportRow task = jdbcTemplate.getJdbcTemplate().queryForObject("""
				SELECT id,user_id,export_type,query_json,scope_json,scope_hash,status,attempt_count,next_attempt_at,
				 storage_config_id,storage_provider,storage_object_key,storage_object_url,file_size,row_count,
				 expires_at,last_error_code,create_time
				FROM scx_adx_report_export_task
				WHERE user_id=? AND scope_hash=? AND query_hash=? AND export_type=? AND idempotency_bucket=?
				""", this::row, userId, scopeHash, queryHash, type, bucket);
		if (task == null) {
			throw new IllegalStateException("导出任务创建失败");
		}
		audit(task, "CREATE", "SUCCESS", null, "任务已进入异步队列");
		return view(task);
	}

	public UnifiedReportViews.ExportTask detail(String id) {
		ExportRow task = requireOwned(id);
		requirePermission(task.type());
		requireCurrentScope(task);
		return view(task);
	}

	public UnifiedReportViews.ExportTask cancel(String id) {
		ExportRow task = requireOwned(id);
		requirePermission(task.type());
		requireCurrentScope(task);
		int updated = jdbcTemplate.getJdbcTemplate().update("""
				UPDATE scx_adx_report_export_task SET status='CANCELLED',cancelled_at=NOW(3),update_time=NOW(3)
				WHERE id=? AND user_id=? AND status IN ('PENDING','RETRY_WAIT','FAILED')
				""", id, SecurityUtils.getUserId());
		if (updated == 0 && !"CANCELLED".equals(task.status())) {
			throw new IllegalStateException("当前导出状态不能取消");
		}
		ExportRow cancelled = requireOwned(id);
		audit(cancelled, "CANCEL", "SUCCESS", null, "导出任务已取消");
		return view(cancelled);
	}

	public ExportDownload download(String id) {
		ExportRow task = requireOwned(id);
		requirePermission(task.type());
		requireCurrentScope(task);
		if (task.expiresAt() == null || !LocalDateTime.now().isBefore(task.expiresAt())) {
			markExpired(task);
			throw new ResponseStatusException(HttpStatus.GONE, "导出文件已过期");
		}
		if (!"SUCCEEDED".equals(task.status()) || !completeStorageReference(task)) {
			throw new IllegalStateException("导出文件尚未就绪");
		}
		audit(task, "DOWNLOAD", "SUCCESS", task.rowCount(), "已再次鉴权并打开受管对象");
		ManagedStorageObject object = managed(task);
		return new ExportDownload("scx-report-" + task.id() + ".csv", "text/csv;charset=UTF-8",
				task.fileSize() == null ? 0 : task.fileSize(), storageUploadService.open(object));
	}

	@Scheduled(fixedDelay = 5000)
	public void scheduledProcess() {
		if (!properties.isExportWorkerEnabled()) {
			return;
		}
		for (int index = 0; index < 5; index++) {
			if (!processNext()) {
				break;
			}
		}
		cleanupExpired();
	}

	boolean processNext() {
		ExportRow task = transactionTemplate.execute(ignored -> claim());
		if (task == null) {
			return false;
		}
		try {
			AdxUnifiedReportService.FrozenScope storedScope = objectMapper.readValue(task.scopeJson(),
					AdxUnifiedReportService.FrozenScope.class);
			if (!sha256(json(reportService.scopeForUser(task.userId()))).equals(task.scopeHash())) {
				throw new ExportFailure("SCOPE_CHANGED");
			}
			ExportEnvelope envelope = objectMapper.readValue(task.queryJson(), ExportEnvelope.class);
			RenderedExport rendered = STANDARD.equals(task.type()) ? renderStandard(envelope, storedScope)
					: renderDeduction(envelope.query(), storedScope);
			ManagedStorageObject object = storageUploadService.uploadManaged(new ByteArrayInputStream(rendered.bytes()),
					"scx-report-" + task.id() + ".csv", "text/csv;charset=UTF-8", rendered.bytes().length);
			jdbcTemplate.getJdbcTemplate()
				.update("""
						UPDATE scx_adx_report_export_task
						SET status='SUCCEEDED',storage_config_id=?,storage_provider=?,storage_object_key=?,storage_object_url=?,
						 file_size=?,row_count=?,expires_at=DATE_ADD(NOW(3),INTERVAL ? SECOND),lease_owner=NULL,
						 lease_until=NULL,last_error_code=NULL,update_time=NOW(3) WHERE id=? AND status='PROCESSING'
						""",
						object.storageConfigId(), object.provider(), object.objectKey(), object.url(),
						rendered.bytes().length, rendered.rows(), properties.getExportTtlSeconds(), task.id());
			audit(task, "SUCCEED", "SUCCESS", rendered.rows(), "受管导出对象已生成");
		}
		catch (Exception error) {
			String code = error instanceof ExportFailure failure ? failure.code() : "RENDER_OR_STORAGE_FAILED";
			boolean terminal = task.attemptCount() + 1 >= 3;
			jdbcTemplate.getJdbcTemplate().update("""
					UPDATE scx_adx_report_export_task SET status=?,attempt_count=attempt_count+1,
					 next_attempt_at=DATE_ADD(NOW(3),INTERVAL 60 SECOND),lease_owner=NULL,lease_until=NULL,
					 last_error_code=?,update_time=NOW(3) WHERE id=? AND status='PROCESSING'
					""", terminal ? "FAILED" : "RETRY_WAIT", code, task.id());
			audit(task, terminal ? "FAIL" : "RETRY", "FAILED", null, code);
			log.warn("Report export task failed: taskId={}, code={}", task.id(), code);
		}
		return true;
	}

	private ExportRow claim() {
		List<ExportRow> candidates = jdbcTemplate.getJdbcTemplate().query("""
				SELECT id,user_id,export_type,query_json,scope_json,scope_hash,status,attempt_count,next_attempt_at,
				 storage_config_id,storage_provider,storage_object_key,storage_object_url,file_size,row_count,
				 expires_at,last_error_code,create_time
				FROM scx_adx_report_export_task
				WHERE ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=NOW(3))
				 OR (status='PROCESSING' AND lease_until<NOW(3)))
				ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
				""", this::row);
		if (candidates.isEmpty()) {
			return null;
		}
		ExportRow task = candidates.get(0);
		jdbcTemplate.getJdbcTemplate().update("""
				UPDATE scx_adx_report_export_task SET status='PROCESSING',lease_owner=?,
				 lease_until=DATE_ADD(NOW(3),INTERVAL 120 SECOND),update_time=NOW(3) WHERE id=?
				""", "s8-export-worker", task.id());
		audit(task, "CLAIM", "SUCCESS", null, "worker 已领取任务");
		return task;
	}

	private RenderedExport renderStandard(ExportEnvelope envelope, AdxUnifiedReportService.FrozenScope scope) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		long totalRows = 0;
		long current = 1;
		boolean header = false;
		List<String> metricCodes = List.of();
		while (true) {
			UnifiedReportQuery base = envelope.query();
			UnifiedReportQuery pageQuery = new UnifiedReportQuery(base.startDate(), base.endDate(), base.grain(),
					base.groupBy(), base.mediaLevel(), base.advertiserId(), base.productId(), base.appLinkId(),
					base.taskId(), base.channelId(), base.mediaAccountId(), base.metrics(), base.sort(),
					base.direction(), current, properties.getMaximumPageSize());
			UnifiedReportViews.Page page = reportService.queryForExport(pageQuery, scope, envelope.sensitive());
			if (page.total() > properties.getMaximumExportRows()) {
				throw new ExportFailure("ROW_LIMIT_EXCEEDED");
			}
			for (UnifiedReportViews.Record record : page.records()) {
				if (!header) {
					metricCodes = record.metrics().keySet().stream().sorted().toList();
					List<String> metricHeaders = new ArrayList<>();
					for (String metric : metricCodes) {
						metricHeaders.add(metric);
						metricHeaders.add(metric + "Numerator");
						metricHeaders.add(metric + "Denominator");
						metricHeaders.add(metric + "Status");
					}
					writeCsv(output, concat(List.of("dimensionType", "dimensionKey"), metricHeaders));
					header = true;
				}
				List<String> values = new ArrayList<>();
				values.add(record.dimensionType());
				values.add(record.dimensionKey());
				for (String code : metricCodes) {
					UnifiedReportViews.MetricValue metric = record.metrics().get(code);
					values.add(decimal(metric.value()));
					values.add(decimal(metric.numerator()));
					values.add(decimal(metric.denominator()));
					values.add(metric.status());
				}
				writeCsv(output, values);
				totalRows++;
			}
			if (totalRows >= page.total() || page.records().isEmpty()) {
				break;
			}
			current++;
		}
		return new RenderedExport(output.toByteArray(), totalRows);
	}

	private RenderedExport renderDeduction(UnifiedReportQuery query, AdxUnifiedReportService.FrozenScope scope) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("start", query.startDate().atStartOfDay())
			.addValue("end", query.endDate().plusDays(1).atStartOfDay())
			.addValue("limit", properties.getMaximumExportRows() + 1);
		StringBuilder where = new StringBuilder(" WHERE a.stat_hour>=:start AND a.stat_hour<:end");
		appendFilter(where, parameters, "a.advertiser_id", "advertiserId", query.advertiserId());
		appendFilter(where, parameters, "h.product_id", "productId", query.productId());
		appendFilter(where, parameters, "h.app_link_id", "appLinkId", query.appLinkId());
		appendFilter(where, parameters, "a.task_id", "taskId", query.taskId());
		appendFilter(where, parameters, "a.channel_id", "channelId", query.channelId());
		appendFilter(where, parameters, "a.media_account_id", "mediaAccountId", query.mediaAccountId());
		appendRestriction(where, parameters, "a.advertiser_id", "scopeAdvertisers", scope.advertisers());
		appendRestriction(where, parameters, "a.channel_id", "scopeChannels", scope.channels());
		appendRestriction(where, parameters, "a.media_account_id", "scopeMediaAccounts", scope.mediaAccounts());
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT a.stat_hour,a.advertiser_id,a.channel_id,a.media_account_id,a.task_id,a.monitor_route_id,
					 a.route_version_id,a.adjustment_type,a.metric_code,a.delta_count,a.delta_amount,a.reason_code
				FROM scx_adx_hourly_adjustment a
				LEFT JOIN scx_adx_hourly_report h ON h.id=a.source_hourly_fact_id
				""" + where + " ORDER BY a.stat_hour,a.id LIMIT :limit", parameters);
		if (rows.size() > properties.getMaximumExportRows()) {
			throw new ExportFailure("ROW_LIMIT_EXCEEDED");
		}
		List<String> columns = List.of("statHour", "advertiserId", "channelId", "mediaAccountId", "taskId", "routeId",
				"routeVersionId", "type", "metric", "deltaCount", "deltaAmount", "reasonCode");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		writeCsv(output, columns);
		for (Map<String, Object> row : rows) {
			writeCsv(output,
					List.of("stat_hour", "advertiser_id", "channel_id", "media_account_id", "task_id",
							"monitor_route_id", "route_version_id", "adjustment_type", "metric_code", "delta_count",
							"delta_amount", "reason_code")
						.stream()
						.map(column -> row.get(column) == null ? "" : row.get(column).toString())
						.toList());
		}
		return new RenderedExport(output.toByteArray(), rows.size());
	}

	private void cleanupExpired() {
		List<ExportRow> expired = jdbcTemplate.getJdbcTemplate().query("""
				SELECT id,user_id,export_type,query_json,scope_json,scope_hash,status,attempt_count,next_attempt_at,
				 storage_config_id,storage_provider,storage_object_key,storage_object_url,file_size,row_count,
				 expires_at,last_error_code,create_time FROM scx_adx_report_export_task
				WHERE status='SUCCEEDED' AND expires_at<=NOW(3) ORDER BY expires_at LIMIT 20
				""", this::row);
		for (ExportRow task : expired) {
			markExpired(task);
		}
	}

	private void markExpired(ExportRow task) {
		try {
			if (completeStorageReference(task)) {
				storageUploadService.delete(managed(task));
			}
			jdbcTemplate.getJdbcTemplate().update("""
					UPDATE scx_adx_report_export_task SET status='EXPIRED',storage_object_url=NULL,
					 storage_object_key=NULL,update_time=NOW(3) WHERE id=? AND status='SUCCEEDED'
					""", task.id());
			audit(task, "CLEANUP", "SUCCESS", task.rowCount(), "临时受管对象已清理");
		}
		catch (RuntimeException error) {
			audit(task, "CLEANUP", "FAILED", task.rowCount(), "STORAGE_CLEANUP_FAILED");
		}
	}

	private ExportRow requireOwned(String id) {
		List<ExportRow> rows = jdbcTemplate.getJdbcTemplate().query("""
				SELECT id,user_id,export_type,query_json,scope_json,scope_hash,status,attempt_count,next_attempt_at,
				 storage_config_id,storage_provider,storage_object_key,storage_object_url,file_size,row_count,
				 expires_at,last_error_code,create_time FROM scx_adx_report_export_task WHERE id=? AND user_id=?
				""", this::row, id, SecurityUtils.getUserId());
		if (rows.isEmpty()) {
			throw new BootBusinessException(HttpStatus.FORBIDDEN.value(), "对象不存在或无权访问");
		}
		return rows.get(0);
	}

	private void requireCurrentScope(ExportRow task) {
		String currentHash = sha256(json(reportService.scopeForUser(task.userId())));
		if (!task.scopeHash().equals(currentHash)) {
			throw new BootBusinessException(HttpStatus.FORBIDDEN.value(), "对象范围已变化，请重新创建导出");
		}
	}

	private void requirePermission(String type) {
		StpUtil.checkPermission(DEDUCTION.equals(type) ? "adx:report:deduction:export" : "adx:report:export");
	}

	private void validateExportRange(UnifiedReportQuery query) {
		if (query.startDate() == null || query.endDate() == null || query.startDate().isAfter(query.endDate())) {
			throw new IllegalArgumentException("导出必须提供有效的开始与结束日期");
		}
		if (query.startDate().plusDays(properties.getMaximumExportDays() - 1L).isBefore(query.endDate())) {
			throw new IllegalArgumentException("导出跨度超过允许上限");
		}
	}

	private ExportRow row(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ExportRow(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
				resultSet.getString(4), resultSet.getString(5), resultSet.getString(6), resultSet.getString(7),
				resultSet.getInt(8),
				resultSet.getTimestamp(9) == null ? null : resultSet.getTimestamp(9).toLocalDateTime(),
				resultSet.getString(10), resultSet.getString(11), resultSet.getString(12), resultSet.getString(13),
				resultSet.getObject(14, Long.class), resultSet.getObject(15, Long.class),
				resultSet.getTimestamp(16) == null ? null : resultSet.getTimestamp(16).toLocalDateTime(),
				resultSet.getString(17), resultSet.getTimestamp(18).toLocalDateTime());
	}

	private UnifiedReportViews.ExportTask view(ExportRow task) {
		return new UnifiedReportViews.ExportTask(task.id(), task.type(), task.status(), task.expiresAt(),
				task.lastErrorCode(), task.createTime());
	}

	private void audit(ExportRow task, String action, String result, Long rows, String detail) {
		jdbcTemplate.getJdbcTemplate().update("""
				INSERT INTO scx_adx_report_export_audit
				(id,export_task_id,user_id,action,result,query_hash,scope_hash,row_count,detail_summary,create_time)
				SELECT ?,id,user_id,?,?,query_hash,scope_hash,?,?,NOW(3) FROM scx_adx_report_export_task WHERE id=?
				""", id(), action, result, rows, detail, task.id());
	}

	private void appendFilter(StringBuilder where, MapSqlParameterSource parameters, String column, String name,
			String value) {
		if (StringUtils.hasText(value)) {
			where.append(" AND ").append(column).append("=:").append(name);
			parameters.addValue(name, value);
		}
	}

	private void appendRestriction(StringBuilder where, MapSqlParameterSource parameters, String column, String name,
			Restriction restriction) {
		if (restriction.unrestricted()) {
			return;
		}
		if (restriction.ids().isEmpty()) {
			where.append(" AND 1=0");
			return;
		}
		where.append(" AND ").append(column).append(" IN (:").append(name).append(')');
		parameters.addValue(name, restriction.ids());
	}

	private ManagedStorageObject managed(ExportRow task) {
		return new ManagedStorageObject(task.storageConfigId(), task.storageProvider(), task.storageObjectKey(),
				task.storageObjectUrl());
	}

	private boolean completeStorageReference(ExportRow task) {
		return StringUtils.hasText(task.storageConfigId()) && StringUtils.hasText(task.storageProvider())
				&& StringUtils.hasText(task.storageObjectKey()) && StringUtils.hasText(task.storageObjectUrl());
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException error) {
			throw new IllegalArgumentException("导出查询无法序列化", error);
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	private List<String> concat(List<String> left, List<String> right) {
		List<String> values = new ArrayList<>(left);
		values.addAll(right);
		return values;
	}

	private void writeCsv(ByteArrayOutputStream output, List<String> values) {
		String line = values.stream().map(this::csv).collect(java.util.stream.Collectors.joining(",")) + "\n";
		output.writeBytes(line.getBytes(StandardCharsets.UTF_8));
	}

	private String csv(String value) {
		String safe = value == null ? "" : value;
		return '"' + safe.replace("\"", "\"\"") + '"';
	}

	private String decimal(BigDecimal value) {
		return value == null ? "" : value.toPlainString();
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	public record ExportDownload(String fileName, String contentType, long size, InputStream inputStream) {
	}

	private record ExportEnvelope(UnifiedReportQuery query, boolean sensitive) {
	}

	private record RenderedExport(byte[] bytes, long rows) {
	}

	private record ExportRow(String id, String userId, String type, String queryJson, String scopeJson,
			String scopeHash, String status, int attemptCount, LocalDateTime nextAttemptAt, String storageConfigId,
			String storageProvider, String storageObjectKey, String storageObjectUrl, Long fileSize, Long rowCount,
			LocalDateTime expiresAt, String lastErrorCode, LocalDateTime createTime) {
	}

	private static final class ExportFailure extends RuntimeException {

		private final String code;

		private ExportFailure(String code) {
			super(code);
			this.code = code;
		}

		private String code() {
			return code;
		}

	}

}
