package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OcpxFlowLogService {

	private static final Set<String> FLOW_TYPES = Set.of("FEEDBACK", "CALLBACK");

	private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED");

	private static final Pattern SENSITIVE_CALLBACK_QUERY_VALUE = Pattern.compile(
			"(?i)([?&](?:access_token|api_key|client_secret|password|satoken|secret|signature|sign|token|key)=)[^&#]*");

	private final JdbcTemplate jdbcTemplate;

	private final AdvertiserScope advertiserScope;

	public FlowLogViews.Page query(LocalDate startDate, LocalDate endDate, String flowType, String resultStatus,
			String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId, String eventId,
			Long currentValue, Long sizeValue) {
		LocalDate end = endDate == null ? LocalDate.now() : endDate;
		LocalDate start = startDate == null ? end.minusDays(6) : startDate;
		if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
			throw new BootBusinessException("日志时间范围无效");
		}
		String normalizedType = normalize(flowType, FLOW_TYPES, "flowType");
		String normalizedStatus = normalize(resultStatus, STATUSES, "resultStatus");
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		long current = currentValue == null ? 1 : Math.max(1, currentValue);
		long size = sizeValue == null ? 30 : Math.min(100, Math.max(1, sizeValue));
		StringBuilder where = new StringBuilder(" WHERE f.event_time>=? AND f.event_time<?");
		List<Object> args = new ArrayList<>();
		args.add(start.atStartOfDay());
		args.add(end.plusDays(1).atStartOfDay());
		filter(where, args, "f.flow_type", normalizedType);
		filter(where, args, "f.result_status", normalizedStatus);
		filter(where, args, "f.advertiser_id", scopedAdvertiserId);
		filter(where, args, "f.promotion_link_id", promotionLinkId);
		filter(where, args, "f.media_id", mediaId);
		filter(where, args, "f.monitor_task_id", monitorTaskId);
		filter(where, args, "f.event_id", eventId);
		long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_flow_log f" + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size);
		pageArgs.add((current - 1) * size);
		String sql = """
				SELECT f.id,f.flow_type,f.event_id,f.source_event_id,f.event_code,
				       f.advertiser_id,a.advertiser_name,f.promotion_link_id,p.link_name,
				     f.media_id,m.media_name,f.monitor_task_id,t.monitor_name,f.result_status,
				       f.partner_http_status,f.message,f.duration_ms,f.event_time,f.create_time
				FROM scx_ocpx_flow_log f
				JOIN scx_ocpx_advertiser a ON a.id=f.advertiser_id
				JOIN scx_ocpx_promotion_link p ON p.id=f.promotion_link_id
				JOIN scx_ocpx_media m ON m.id=f.media_id
				JOIN scx_ocpx_monitor_task t ON t.id=f.monitor_task_id
				""" + where + " ORDER BY f.create_time DESC,f.id DESC LIMIT ? OFFSET ?";
		List<FlowLogViews.Item> records = jdbcTemplate.query(sql,
				(rs, rowNum) -> new FlowLogViews.Item(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
						rs.getString(14), (Integer) rs.getObject(15), rs.getString(16), rs.getLong(17),
						time(rs.getTimestamp(18)), time(rs.getTimestamp(19))),
				pageArgs.toArray());
		return new FlowLogViews.Page(records, current, size, total);
	}

	public FlowLogViews.RequestDetail requestDetail(String flowLogId) {
		advertiserScope.requireSystemAdministrator();
		if (!StringUtils.hasText(flowLogId) || flowLogId.length() > 32) {
			throw new BootBusinessException("流程日志 ID 无效");
		}
		List<FlowLogViews.RequestDetail> records = jdbcTemplate.query("""
				SELECT f.id AS flow_log_id,f.flow_type,f.event_id,f.source_event_id,f.event_code,
				       f.result_status,f.partner_http_status,f.message,f.response_preview,f.duration_ms,
				       f.event_time,f.create_time AS flow_created_at,
				       d.id AS feedback_log_id,d.create_time AS feedback_received_at,
				       d.req_id,d.clk_id,d.imp_id,d.userid,d.user_type,d.device_os,
				       d.osv,d.model,d.device_did,d.device_imei_md5,d.device_oaid,d.device_oaid_md5,
				       d.device_dpid,d.device_androidid_md5,d.device_idfa,d.device_idfa_md5,d.aaid,
				       d.device_caid,d.device_caid1,d.device_caid2,d.device_caid_list,d.last_caid,d.prev_caid,
				       d.device_mac,d.ip,d.ipv6,d.ua,d.callback_url,d.ts,d.ts_ms,d.adv_id,d.group_id,
				       d.plan_id,d.creative_id,d.m_id,d.a_id,d.t_id,d.l_id
				FROM scx_ocpx_flow_log f
				LEFT JOIN scx_ocpx_log_feedback d
				  ON d.req_id=CASE WHEN COALESCE(f.source_event_id,'')<>'' THEN f.source_event_id ELSE f.event_id END
				 AND d.m_id=f.media_id AND d.a_id=f.advertiser_id
				 AND d.t_id=f.monitor_task_id AND d.l_id=f.promotion_link_id
				WHERE f.id=?
				ORDER BY d.create_time DESC,d.id DESC
				LIMIT 1
				""", (rs, rowNum) -> requestDetail(rs), flowLogId.trim());
		if (records.isEmpty()) {
			throw new BootBusinessException(404, "流程日志不存在");
		}
		return records.get(0);
	}

	private FlowLogViews.RequestDetail requestDetail(ResultSet rs) throws SQLException {
		FlowLogViews.RequestDetail detail = new FlowLogViews.RequestDetail();
		detail.setFlowLogId(rs.getString("flow_log_id"));
		detail.setFlowType(rs.getString("flow_type"));
		detail.setEventId(rs.getString("event_id"));
		detail.setSourceEventId(rs.getString("source_event_id"));
		detail.setEventCode(rs.getString("event_code"));
		detail.setResultStatus(rs.getString("result_status"));
		detail.setPartnerHttpStatus((Integer) rs.getObject("partner_http_status"));
		detail.setMessage(rs.getString("message"));
		detail.setResponsePreview(rs.getString("response_preview"));
		detail.setDurationMs(rs.getLong("duration_ms"));
		detail.setEventTime(time(rs.getTimestamp("event_time")));
		detail.setFlowCreatedAt(time(rs.getTimestamp("flow_created_at")));
		detail.setFeedbackLogId((Long) rs.getObject("feedback_log_id"));
		detail.setReceivedAt(time(rs.getTimestamp("feedback_received_at")));
		detail.setRequestId(rs.getString("req_id"));
		detail.setClickId(rs.getString("clk_id"));
		detail.setImpressionId(rs.getString("imp_id"));
		detail.setUserId(rs.getString("userid"));
		detail.setUserType(rs.getString("user_type"));
		detail.setDeviceOs(rs.getString("device_os"));
		detail.setOsVersion(rs.getString("osv"));
		detail.setModel(rs.getString("model"));
		detail.setImei(rs.getString("device_did"));
		detail.setImeiMd5(rs.getString("device_imei_md5"));
		detail.setOaid(rs.getString("device_oaid"));
		detail.setOaidMd5(rs.getString("device_oaid_md5"));
		detail.setAndroidId(rs.getString("device_dpid"));
		detail.setAndroidIdMd5(rs.getString("device_androidid_md5"));
		detail.setIdfa(rs.getString("device_idfa"));
		detail.setIdfaMd5(rs.getString("device_idfa_md5"));
		detail.setAaid(rs.getString("aaid"));
		detail.setCaid(rs.getString("device_caid"));
		detail.setCaid1(rs.getString("device_caid1"));
		detail.setCaid2(rs.getString("device_caid2"));
		detail.setCaidList(rs.getString("device_caid_list"));
		detail.setLastCaid(rs.getString("last_caid"));
		detail.setPreviousCaid(rs.getString("prev_caid"));
		detail.setMac(rs.getString("device_mac"));
		detail.setIp(rs.getString("ip"));
		detail.setIpv6(rs.getString("ipv6"));
		detail.setUa(rs.getString("ua"));
		detail.setCallbackUrl(sanitizedCallbackUrl(rs.getString("callback_url")));
		detail.setTimestamp(rs.getString("ts"));
		detail.setTimestampMs(rs.getString("ts_ms"));
		detail.setExternalAdvertiserId(rs.getString("adv_id"));
		detail.setGroupId(rs.getString("group_id"));
		detail.setPlanId(rs.getString("plan_id"));
		detail.setCreativeId(rs.getString("creative_id"));
		detail.setMediaId(rs.getString("m_id"));
		detail.setAdvertiserId(rs.getString("a_id"));
		detail.setMonitorTaskId(rs.getString("t_id"));
		detail.setPromotionLinkId(rs.getString("l_id"));
		return detail;
	}

	private String sanitizedCallbackUrl(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
			return SENSITIVE_CALLBACK_QUERY_VALUE.matcher(decoded).replaceAll("$1***");
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String normalize(String value, Set<String> allowed, String name) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String normalized = value.trim().toUpperCase();
		if (!allowed.contains(normalized)) {
			throw new BootBusinessException(name + " 参数无效");
		}
		return normalized;
	}

	private void filter(StringBuilder sql, List<Object> args, String column, String value) {
		if (StringUtils.hasText(value)) {
			sql.append(" AND ").append(column).append("=?");
			args.add(value);
		}
	}

	private LocalDateTime time(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

}
