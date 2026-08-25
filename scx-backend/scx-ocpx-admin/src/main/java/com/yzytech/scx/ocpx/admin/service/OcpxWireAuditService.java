package com.yzytech.scx.ocpx.admin.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.WireAuditViews;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class OcpxWireAuditService {

	private static final Pattern AUDIT_ID = Pattern.compile("[0-9a-f]{32}");
	private static final int EXPORT_LIMIT = 100_000;
	private static final String SELECT = """
			SELECT id,trace_id,flow_log_id,stage_code,direction,traffic_class,protocol,request_method,request_path,
			 wire_sent,result_status,partner_http_status,error_code,request_id,advertiser_id,promotion_link_id,
			 media_id,monitor_task_id,request_bytes,response_bytes,request_sha256,response_sha256,raw_state,
			 key_id,crypto_version,capture_source,captured_at,finished_at,raw_expires_at,metadata_expires_at
			FROM scx_ocpx_wire_audit
			""";

	private final JdbcTemplate jdbcTemplate;
	private final AdvertiserScope advertiserScope;
	private final OcpxKeyRingCrypto crypto;
	private final MeterRegistry meterRegistry;

	public WireAuditViews.Page query(LocalDate startDate, LocalDate endDate, String stageCode, String direction,
			String protocol, String resultStatus, Integer httpStatus, String requestId, String traceId,
			String trafficClass, Boolean wireSent, String rawState, String advertiserId, Long current, Long size) {
		LocalDate end = endDate == null ? LocalDate.now() : endDate;
		LocalDate start = startDate == null ? end.minusDays(6) : startDate;
		validateRange(start, end);
		long page = current == null || current < 1 ? 1 : current;
		long limit = size == null || size < 1 ? 20 : Math.min(size, 100);
		Filter filter = filter(start, end, stageCode, direction, protocol, resultStatus, httpStatus, requestId, traceId,
				trafficClass, wireSent, rawState, advertiserId);
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_wire_audit" + filter.where(),
				Long.class, filter.arguments().toArray());
		List<Object> arguments = new ArrayList<>(filter.arguments());
		arguments.add(limit);
		arguments.add((page - 1) * limit);
		List<WireAuditViews.Item> records = jdbcTemplate.query(SELECT + filter.where()
				+ " ORDER BY captured_at DESC,id DESC LIMIT ? OFFSET ?", (rs, rowNum) -> item(rs), arguments.toArray());
		WireAuditViews.Page result = new WireAuditViews.Page();
		result.setRecords(records);
		result.setTotal(total == null ? 0 : total);
		result.setCurrent(page);
		result.setSize(limit);
		return result;
	}

	public WireAuditViews.Detail detail(String id) {
		advertiserScope.requireSystemAdministratorPermission(SystemAccountConstants.FLOW_LOG_RAW_PERMISSION);
		validateId(id);
		List<WireAuditViews.Detail> rows = jdbcTemplate.query("""
				SELECT a.*,p.payload_aad,p.payload_nonce,p.payload_ciphertext,p.key_id AS payload_key_id,
				 p.crypto_version AS payload_crypto_version
				FROM scx_ocpx_wire_audit a LEFT JOIN scx_ocpx_wire_audit_payload p ON p.audit_id=a.id
				WHERE a.id=?
				""", (rs, rowNum) -> detail(rs), id);
		if (rows.isEmpty())
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "原始请求审计不存在");
		return rows.get(0);
	}

	@Transactional(readOnly = true)
	public void export(LocalDate date, String stageCode, String direction, String protocol, String resultStatus,
			Integer httpStatus, String requestId, String traceId, String trafficClass, Boolean wireSent, String rawState,
			String advertiserId, HttpServletResponse response) throws IOException {
		advertiserScope.requireSystemAdministratorPermission(SystemAccountConstants.FLOW_LOG_RAW_EXPORT_PERMISSION);
		if (date == null)
			throw new BootBusinessException("导出必须指定一个自然日");
		validateRange(date, date);
		Filter filter = filter(date, date, stageCode, direction, protocol, resultStatus, httpStatus, requestId, traceId,
				trafficClass, wireSent, rawState, advertiserId);
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_wire_audit" + filter.where(),
				Long.class, filter.arguments().toArray());
		if (total != null && total > EXPORT_LIMIT)
			throw new BootBusinessException("单次导出不能超过 100000 条，请缩小筛选范围");

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/zip");
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Content-Disposition", "attachment; filename=ocpx-wire-audit-" + date + ".zip");
		try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
			zip.putNextEntry(new ZipEntry("ocpx-wire-audit-" + date + ".ndjson"));
			String sql = """
					SELECT a.*,p.payload_aad,p.payload_nonce,p.payload_ciphertext,p.key_id AS payload_key_id,
					 p.crypto_version AS payload_crypto_version
					FROM scx_ocpx_wire_audit a LEFT JOIN scx_ocpx_wire_audit_payload p ON p.audit_id=a.id
					""" + filter.where() + " ORDER BY a.captured_at,a.id";
			jdbcTemplate.query(sql, rs -> {
				try {
					WireAuditViews.Detail detail = detail(rs);
					zip.write(JSON.toJSONBytes(detail));
					zip.write('\n');
				}
				catch (IOException exception) {
					throw new SQLException("unable to stream encrypted audit export", exception);
				}
			}, filter.arguments().toArray());
			zip.closeEntry();
		}
	}

	private WireAuditViews.Detail detail(ResultSet rs) throws SQLException {
		WireAuditViews.Detail detail = new WireAuditViews.Detail();
		WireAuditViews.Item metadata = item(rs);
		detail.setMetadata(metadata);
		if (metadata.getRawExpiresAt() == null || metadata.getRawExpiresAt().isBefore(LocalDateTime.now())) {
			metadata.setRawState("EXPIRED");
			return detail;
		}
		byte[] cipherText = rs.getBytes("payload_ciphertext");
		if (cipherText == null) {
			return detail;
		}
		try {
			String aad = rs.getString("payload_aad");
			byte[] clear = crypto.decrypt(new OcpxKeyRingCrypto.SealedValue(rs.getString("payload_key_id"),
					rs.getInt("payload_crypto_version"), rs.getBytes("payload_nonce"), cipherText),
					OcpxKeyRingCrypto.PURPOSE_AUDIT, aad.getBytes(StandardCharsets.UTF_8));
			Map<String, Object> envelope = JSON.parseObject(clear, Map.class);
			detail.setRequest(map(envelope.get("request")));
			detail.setResponse(map(envelope.get("response")));
			return detail;
		}
		catch (RuntimeException exception) {
			meterRegistry.counter("scx.ocpx.audit.decrypt.failures").increment();
			throw new BootBusinessException("原始请求解密失败");
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
	}

	private WireAuditViews.Item item(ResultSet rs) throws SQLException {
		WireAuditViews.Item item = new WireAuditViews.Item();
		item.setId(rs.getString("id"));
		item.setTraceId(rs.getString("trace_id"));
		item.setFlowLogId(rs.getString("flow_log_id"));
		item.setStageCode(rs.getString("stage_code"));
		item.setDirection(rs.getString("direction"));
		item.setTrafficClass(rs.getString("traffic_class"));
		item.setProtocol(rs.getString("protocol"));
		item.setRequestMethod(rs.getString("request_method"));
		item.setRequestPath(rs.getString("request_path"));
		item.setWireSent(rs.getBoolean("wire_sent"));
		item.setResultStatus(rs.getString("result_status"));
		int status = rs.getInt("partner_http_status");
		item.setPartnerHttpStatus(rs.wasNull() ? null : status);
		item.setErrorCode(rs.getString("error_code"));
		item.setRequestId(rs.getString("request_id"));
		item.setAdvertiserId(rs.getString("advertiser_id"));
		item.setPromotionLinkId(rs.getString("promotion_link_id"));
		item.setMediaId(rs.getString("media_id"));
		item.setMonitorTaskId(rs.getString("monitor_task_id"));
		item.setRequestBytes(rs.getLong("request_bytes"));
		item.setResponseBytes(rs.getLong("response_bytes"));
		item.setRequestSha256(rs.getString("request_sha256"));
		item.setResponseSha256(rs.getString("response_sha256"));
		item.setRawState(rs.getString("raw_state"));
		item.setKeyId(rs.getString("key_id"));
		item.setCryptoVersion(rs.getInt("crypto_version"));
		item.setCaptureSource(rs.getString("capture_source"));
		item.setCapturedAt(localDateTime(rs, "captured_at"));
		item.setFinishedAt(localDateTime(rs, "finished_at"));
		item.setRawExpiresAt(localDateTime(rs, "raw_expires_at"));
		item.setMetadataExpiresAt(localDateTime(rs, "metadata_expires_at"));
		if (item.getRawExpiresAt() != null && item.getRawExpiresAt().isBefore(LocalDateTime.now()))
			item.setRawState("EXPIRED");
		return item;
	}

	private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
		return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
	}

	private Filter filter(LocalDate start, LocalDate end, String stageCode, String direction, String protocol,
			String resultStatus, Integer httpStatus, String requestId, String traceId, String trafficClass,
			Boolean wireSent, String rawState, String advertiserId) {
		StringBuilder where = new StringBuilder(" WHERE captured_at>=? AND captured_at<?");
		List<Object> arguments = new ArrayList<>();
		arguments.add(start.atStartOfDay());
		arguments.add(end.plusDays(1).atStartOfDay());
		append(where, arguments, "stage_code", stageCode, 32);
		append(where, arguments, "direction", direction, 16);
		append(where, arguments, "protocol", protocol, 32);
		append(where, arguments, "result_status", resultStatus, 16);
		if (httpStatus != null) {
			where.append(" AND partner_http_status=?");
			arguments.add(httpStatus);
		}
		append(where, arguments, "request_id", requestId, 128);
		append(where, arguments, "trace_id", traceId, 64);
		append(where, arguments, "traffic_class", trafficClass, 16);
		if (wireSent != null) {
			where.append(" AND wire_sent=?");
			arguments.add(wireSent);
		}
		if (StringUtils.hasText(rawState)) {
			String state = rawState.trim();
			if (state.length() > 16)
				throw new BootBusinessException("审计查询条件过长");
			if ("EXPIRED".equals(state)) {
				where.append(" AND raw_expires_at<NOW(3)");
			}
			else {
				where.append(" AND raw_state=? AND raw_expires_at>=NOW(3)");
				arguments.add(state);
			}
		}
		String scopedAdvertiser = advertiserScope.constrain(advertiserId);
		if (StringUtils.hasText(scopedAdvertiser)) {
			where.append(" AND advertiser_id=?");
			arguments.add(scopedAdvertiser);
		}
		return new Filter(where.toString(), arguments);
	}

	private void append(StringBuilder where, List<Object> arguments, String column, String value, int maxLength) {
		if (!StringUtils.hasText(value))
			return;
		String trimmed = value.trim();
		if (trimmed.length() > maxLength)
			throw new BootBusinessException("审计查询条件过长");
		where.append(" AND ").append(column).append("=?");
		arguments.add(trimmed);
	}

	private void validateRange(LocalDate start, LocalDate end) {
		if (start.isAfter(end) || end.isAfter(LocalDate.now()) || start.isBefore(LocalDate.now().minusDays(180)))
			throw new BootBusinessException("审计查询日期必须在最近 180 天内");
	}

	private void validateId(String id) {
		if (id == null || !AUDIT_ID.matcher(id).matches())
			throw new BootBusinessException("原始请求审计 ID 无效");
	}

	private record Filter(String where, List<Object> arguments) {
	}

}
