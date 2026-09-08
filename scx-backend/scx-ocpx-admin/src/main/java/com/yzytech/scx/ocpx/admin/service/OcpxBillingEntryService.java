package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.BillingEntryViews;
import lombok.RequiredArgsConstructor;
import com.yzytech.scx.ocpx.admin.mapper.OcpxBillingEntrySqlMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxBillingEntryService {

	private final OcpxBillingEntrySqlMapper sqlMapper;

	private static final Set<String> ENTRY_TYPES = Set.of("RECHARGE", "MANUAL_DEBIT", "CLICK_CHARGE",
			"CONVERSION_CHARGE");

	private static final Set<String> STATUSES = Set.of("POSTED", "REJECTED");

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
		var query = new OcpxBillingEntrySqlMapper.Query(scopedAdvertiserId, trimFilter(monitorTaskId),
				trimFilter(entryType), trimFilter(status), trimFilter(businessKey), startTime, endTime, normalizedSize,
				(normalizedCurrent - 1) * normalizedSize);
		Long total = sqlMapper.count(query);
		List<BillingEntryViews.Item> records = sqlMapper.selectPage(query)
			.stream()
			.map(rs -> new BillingEntryViews.Item(rs.id(), rs.advertiserId(), rs.advertiserName(), rs.monitorTaskId(),
					rs.monitorName(), rs.eventFactId(), rs.entryType(), rs.status(), rs.amount(), rs.balanceBefore(),
					rs.balanceAfter(), rs.businessKey(), rs.reasonCode(), rs.operatorUserId(), rs.remark(),
					rs.occurredAt().toLocalDateTime()))
			.toList();
		Page<BillingEntryViews.Item> page = new Page<>(normalizedCurrent, normalizedSize, total == null ? 0 : total);
		page.setRecords(records);
		return page;
	}

	private String trimFilter(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private void validateFilter(String value, Set<String> allowed, String field) {
		if (StringUtils.hasText(value) && !allowed.contains(value.trim())) {
			throw new BootBusinessException(field + " 取值无效");
		}
	}

}
