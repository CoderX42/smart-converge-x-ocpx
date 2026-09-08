package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.CallbackRecordViews;
import lombok.RequiredArgsConstructor;
import com.yzytech.scx.ocpx.admin.mapper.OcpxCallbackRecordSqlMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxCallbackRecordService {

	private final OcpxCallbackRecordSqlMapper sqlMapper;

	private static final Set<String> EVENT_CODES = Set.of("ACTIVATE", "EVOKE", "LOGIN", "REGISTER", "PAYMENT",
			"CUSTOMER_ACQUISITION", "NEXT_STAY", "VISITS", "AUTHENTICATION", "ORDER", "FINISH_THE_ORDER", "EVOKE_C",
			"EVOKE_FIRST", "EVOKE_C_VALID", "LAHUO", "LAHUI", "HUIFANG", "FIRST_ACTIVATION", "FIRST_ORDER", "DOWNLOAD",
			"ACTIVITY", "ADDICTION");

	private static final Set<String> DEDUCTION_DECISIONS = Set.of("PENDING", "PASSED", "DEDUCTED", "UNAVAILABLE");

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

		var query = new OcpxCallbackRecordSqlMapper.Query(start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
				scopedAdvertiserId, normalizedPromotionLinkId, normalizedMediaId, normalizedMonitorTaskId,
				normalizedEventCode, normalizedDecision, normalizedRequestId, targetConversion, size,
				(current - 1) * size);
		Long totalValue = sqlMapper.count(query);
		List<CallbackRecordViews.Row> records = sqlMapper.selectPage(query)
			.stream()
			.map(rs -> new CallbackRecordViews.Row(rs.id(), rs.requestId(), rs.eventCode(), rs.targetConversion(),
					rs.advertiserId(), rs.advertiserName(), rs.promotionLinkId(), rs.linkName(), rs.mediaId(),
					rs.mediaName(), rs.monitorTaskId(), rs.monitorName(), rs.billingStatus(), rs.billingReasonCode(),
					rs.spendAmount(), rs.currencyAmount(), rs.deductionDecision(), rs.deductionRatioSnapshot(),
					time(rs.deductionDecidedAt()), time(rs.receivedAt())))
			.toList();
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

	private LocalDateTime time(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

}
