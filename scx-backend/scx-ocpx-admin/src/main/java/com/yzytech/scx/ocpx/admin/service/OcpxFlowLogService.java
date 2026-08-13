package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxFlowLogService {

	private static final Set<String> FLOW_TYPES = Set.of("FEEDBACK", "CALLBACK");
	private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED");

	private final JdbcTemplate jdbcTemplate;

	public FlowLogViews.Page query(LocalDate startDate, LocalDate endDate, String flowType, String resultStatus,
			String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId, String eventId,
			Long currentValue, Long sizeValue) {
		LocalDate end = endDate == null ? LocalDate.now() : endDate;
		LocalDate start = startDate == null ? end.minusDays(6) : startDate;
		if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
			throw new IllegalArgumentException("日志时间范围无效");
		}
		String normalizedType = normalize(flowType, FLOW_TYPES, "flowType");
		String normalizedStatus = normalize(resultStatus, STATUSES, "resultStatus");
		long current = currentValue == null ? 1 : Math.max(1, currentValue);
		long size = sizeValue == null ? 30 : Math.min(100, Math.max(1, sizeValue));
		StringBuilder where = new StringBuilder(" WHERE f.event_time>=? AND f.event_time<?");
		List<Object> args = new ArrayList<>();
		args.add(start.atStartOfDay());
		args.add(end.plusDays(1).atStartOfDay());
		filter(where, args, "f.flow_type", normalizedType);
		filter(where, args, "f.result_status", normalizedStatus);
		filter(where, args, "f.advertiser_id", advertiserId);
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

	private String normalize(String value, Set<String> allowed, String name) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String normalized = value.trim().toUpperCase();
		if (!allowed.contains(normalized)) {
			throw new IllegalArgumentException(name + " 参数无效");
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
