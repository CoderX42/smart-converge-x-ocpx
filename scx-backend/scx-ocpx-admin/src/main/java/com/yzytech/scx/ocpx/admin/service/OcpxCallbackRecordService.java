package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.CallbackRecordViews;
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
public class OcpxCallbackRecordService {

	private static final Set<String> EVENT_CODES = Set.of("ACTIVATE", "EVOKE", "LOGIN", "REGISTER", "PAYMENT",
			"CUSTOMER_ACQUISITION", "NEXT_STAY", "VISITS", "AUTHENTICATION", "ORDER", "FINISH_THE_ORDER", "EVOKE_C",
			"EVOKE_FIRST", "EVOKE_C_VALID", "LAHUO", "LAHUI", "HUIFANG", "FIRST_ACTIVATION", "FIRST_ORDER");

	private static final Set<String> DEDUCTION_DECISIONS = Set.of("PENDING", "PASSED", "DEDUCTED", "UNAVAILABLE");

	private final JdbcTemplate jdbcTemplate;

	private final AdvertiserScope advertiserScope;

	public CallbackRecordViews.Page query(LocalDate startDate, LocalDate endDate, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId, String eventCode, Boolean targetConversion,
			String deductionDecision, String requestId, Long currentValue, Long sizeValue) {
		LocalDate end = endDate == null ? LocalDate.now() : endDate;
		LocalDate start = startDate == null ? end.minusDays(6) : startDate;
		if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
			throw new BootBusinessException("转化记录时间范围无效");
		}
		String scopedAdvertiserId = advertiserScope.constrain(limited(advertiserId, 32, "advertiserId"));
		String normalizedPromotionLinkId = limited(promotionLinkId, 32, "promotionLinkId");
		String normalizedMediaId = limited(mediaId, 32, "mediaId");
		String normalizedMonitorTaskId = limited(monitorTaskId, 32, "monitorTaskId");
		String normalizedEventCode = normalize(eventCode, EVENT_CODES, "eventCode");
		String normalizedDecision = normalize(deductionDecision, DEDUCTION_DECISIONS, "deductionDecision");
		String normalizedRequestId = limited(requestId, 128, "requestId");
		long current = currentValue == null ? 1 : Math.max(1, currentValue);
		long size = sizeValue == null ? 30 : Math.min(100, Math.max(1, sizeValue));

		StringBuilder where = new StringBuilder(
				" WHERE f.flow_type='CALLBACK' AND f.received_at>=? AND f.received_at<?");
		List<Object> args = new ArrayList<>();
		args.add(start.atStartOfDay());
		args.add(end.plusDays(1).atStartOfDay());
		filter(where, args, "f.advertiser_id", scopedAdvertiserId);
		filter(where, args, "f.promotion_link_id", normalizedPromotionLinkId);
		filter(where, args, "f.media_id", normalizedMediaId);
		filter(where, args, "f.monitor_task_id", normalizedMonitorTaskId);
		filter(where, args, "f.event_code", normalizedEventCode);
		if ("UNAVAILABLE".equals(normalizedDecision)) {
			where.append(" AND f.deduction_decision IS NULL");
		}
		else {
			filter(where, args, "f.deduction_decision", normalizedDecision);
		}
		filter(where, args, "f.request_id", normalizedRequestId);
		if (targetConversion != null) {
			where.append(" AND f.target_conversion=?");
			args.add(targetConversion ? 1 : 0);
		}

		String indexHint = " FORCE INDEX (idx_ocpx_event_fact_callback_page)";
		Long totalValue = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_event_fact f" + indexHint + where,
				Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size);
		pageArgs.add((current - 1) * size);
		String sql = """
				SELECT f.id,f.request_id,f.event_code,f.target_conversion,
				       f.advertiser_id,a.advertiser_name,f.promotion_link_id,p.link_name,
				       f.media_id,m.media_name,f.monitor_task_id,t.monitor_name,
				       f.billing_status,f.billing_reason_code,f.spend_amount,f.currency_amount,
				       f.deduction_decision,f.deduction_ratio_snapshot,f.deduction_decided_at,f.received_at
				FROM scx_ocpx_event_fact f FORCE INDEX (idx_ocpx_event_fact_callback_page)
				LEFT JOIN scx_ocpx_advertiser a ON a.id=f.advertiser_id
				LEFT JOIN scx_ocpx_promotion_link p ON p.id=f.promotion_link_id
				LEFT JOIN scx_ocpx_media m ON m.id=f.media_id
				LEFT JOIN scx_ocpx_monitor_task t ON t.id=f.monitor_task_id
				""" + where + " ORDER BY f.received_at DESC,f.id DESC LIMIT ? OFFSET ?";
		List<CallbackRecordViews.Row> records = jdbcTemplate.query(sql,
				(rs, rowNum) -> new CallbackRecordViews.Row(rs.getString("id"), rs.getString("request_id"),
						rs.getString("event_code"), rs.getBoolean("target_conversion"), rs.getString("advertiser_id"),
						rs.getString("advertiser_name"), rs.getString("promotion_link_id"), rs.getString("link_name"),
						rs.getString("media_id"), rs.getString("media_name"), rs.getString("monitor_task_id"),
						rs.getString("monitor_name"), rs.getString("billing_status"),
						rs.getString("billing_reason_code"), rs.getBigDecimal("spend_amount"),
						rs.getBigDecimal("currency_amount"), rs.getString("deduction_decision"),
						rs.getBigDecimal("deduction_ratio_snapshot"), time(rs.getTimestamp("deduction_decided_at")),
						time(rs.getTimestamp("received_at"))),
				pageArgs.toArray());
		return new CallbackRecordViews.Page(records, current, size, totalValue == null ? 0 : totalValue);
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

	private String limited(String value, int length, String name) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.length() > length) {
			throw new BootBusinessException(name + " 参数过长");
		}
		return normalized;
	}

	private void filter(StringBuilder sql, List<Object> args, String column, String value) {
		if (StringUtils.hasText(value)) {
			sql.append(" AND ").append(column).append("=?");
			args.add(value.trim());
		}
	}

	private LocalDateTime time(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

}
