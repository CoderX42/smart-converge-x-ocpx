package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.BillingEntryViews;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxBillingEntryService {

	private static final Set<String> ENTRY_TYPES = Set.of("RECHARGE", "MANUAL_DEBIT", "CLICK_CHARGE",
			"CONVERSION_CHARGE");

	private static final Set<String> STATUSES = Set.of("POSTED", "REJECTED");

	private final JdbcTemplate jdbcTemplate;

	private final AdvertiserScope advertiserScope;

	public Page<BillingEntryViews.Item> list(long current, long size, String advertiserId, String monitorTaskId,
			String entryType, String status, String businessKey, LocalDateTime startTime, LocalDateTime endTime) {
		validateFilter(entryType, ENTRY_TYPES, "entryType");
		validateFilter(status, STATUSES, "status");
		if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
			throw new BootBusinessException("账务流水时间范围无效");
		}
		long normalizedCurrent = Math.max(1, current);
		long normalizedSize = Math.min(100, Math.max(1, size));
		String scopedAdvertiserId = advertiserScope.accountAdvertiserId(advertiserId);
		StringBuilder from = new StringBuilder("""
				 FROM scx_ocpx_billing_entry b
				 JOIN scx_ocpx_advertiser a ON a.id=b.advertiser_id
				 LEFT JOIN scx_ocpx_monitor_task t ON t.id=b.monitor_task_id
				 WHERE 1=1
				""");
		List<Object> args = new ArrayList<>();
		filter(from, args, "b.advertiser_id", scopedAdvertiserId);
		filter(from, args, "b.monitor_task_id", monitorTaskId);
		filter(from, args, "b.entry_type", entryType);
		filter(from, args, "b.status", status);
		if (StringUtils.hasText(businessKey)) {
			from.append(" AND b.business_key LIKE ?");
			args.add("%" + businessKey.trim() + "%");
		}
		if (startTime != null) {
			from.append(" AND b.occurred_at>=?");
			args.add(startTime);
		}
		if (endTime != null) {
			from.append(" AND b.occurred_at<=?");
			args.add(endTime);
		}
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
		String select = """
				SELECT b.id,b.advertiser_id,a.advertiser_name,b.monitor_task_id,t.monitor_name,
				       b.event_fact_id,b.entry_type,b.status,b.amount,b.balance_before,b.balance_after,
				       b.business_key,b.reason_code,b.operator_user_id,b.remark,b.occurred_at
				""" + from + " ORDER BY b.occurred_at DESC,b.id DESC LIMIT ? OFFSET ?";
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(normalizedSize);
		pageArgs.add((normalizedCurrent - 1) * normalizedSize);
		List<BillingEntryViews.Item> records = jdbcTemplate.query(select,
				(rs, rowNum) -> new BillingEntryViews.Item(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getString(12),
						rs.getString(13), rs.getString(14), rs.getString(15), rs.getTimestamp(16).toLocalDateTime()),
				pageArgs.toArray());
		Page<BillingEntryViews.Item> page = new Page<>(normalizedCurrent, normalizedSize, total == null ? 0 : total);
		page.setRecords(records);
		return page;
	}

	private void filter(StringBuilder sql, List<Object> args, String column, String value) {
		if (StringUtils.hasText(value)) {
			sql.append(" AND ").append(column).append("=?");
			args.add(value.trim());
		}
	}

	private void validateFilter(String value, Set<String> allowed, String field) {
		if (StringUtils.hasText(value) && !allowed.contains(value.trim())) {
			throw new BootBusinessException(field + " 取值无效");
		}
	}

}
