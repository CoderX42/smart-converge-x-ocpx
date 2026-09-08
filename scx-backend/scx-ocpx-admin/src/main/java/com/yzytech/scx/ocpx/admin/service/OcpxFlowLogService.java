package com.yzytech.scx.ocpx.admin.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.FlowLogViews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yzytech.scx.ocpx.admin.mapper.OcpxFlowLogSqlMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcpxFlowLogService {

	private final OcpxFlowLogSqlMapper sqlMapper;

	private static final Set<String> FLOW_TYPES = Set.of("FEEDBACK", "CALLBACK");

	private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED");

	private static final Set<String> STAGES = Set.of("LEGACY", "MEDIA_INGRESS", "ADVERTISER_DELIVERY",
			"ADVERTISER_CALLBACK", "CALLBACK_ACCOUNTING", "MEDIA_DEDUCTION", "MEDIA_DELIVERY", "RTA_GATE",
			"RTA_DECISION");

	private static final long MAX_PAGEABLE_TOTAL = 10_000L;

	private static final Pattern SENSITIVE_CALLBACK_QUERY_VALUE = Pattern.compile(
			"(?i)([?&](?:access_token|api_key|client_secret|password|satoken|secret|signature|sign|token|key)=)[^&#]*");

	private final AdvertiserScope advertiserScope;

	public FlowLogViews.Options options(String advertiserId, String mediaId) {
		String currentAdvertiserId = advertiserScope.currentAdvertiserId();
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		List<FlowLogViews.Option> advertisers = sqlMapper.selectAdvertiserOptions(currentAdvertiserId);
		List<FlowLogViews.Option> promotionLinks = sqlMapper.selectLinkOptions(scopedAdvertiserId);
		List<FlowLogViews.Option> media = sqlMapper.selectMediaOptions(currentAdvertiserId);
		List<FlowLogViews.Option> monitorTasks = sqlMapper.selectTaskOptions(currentAdvertiserId, mediaId);
		return new FlowLogViews.Options(advertisers, promotionLinks, media, monitorTasks);
	}

	public FlowLogViews.Page query(LocalDate startDate, LocalDate endDate, String flowType, String resultStatus,
			String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId, String eventId,
			Long currentValue, Long sizeValue) {
		return query(startDate, endDate, flowType, resultStatus, advertiserId, promotionLinkId, mediaId, monitorTaskId,
				eventId, null, currentValue, sizeValue);
	}

	public FlowLogViews.Page query(LocalDate startDate, LocalDate endDate, String flowType, String resultStatus,
			String advertiserId, String promotionLinkId, String mediaId, String monitorTaskId, String eventId,
			String stageCode, Long currentValue, Long sizeValue) {
		LocalDate end = endDate == null ? LocalDate.now() : endDate;
		LocalDate start = startDate == null ? end.minusDays(6) : startDate;
		if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
			throw new BootBusinessException("日志时间范围无效");
		}
		String normalizedType = normalize(flowType, FLOW_TYPES, "flowType");
		String normalizedStatus = normalize(resultStatus, STATUSES, "resultStatus");
		String normalizedStage = normalize(stageCode, STAGES, "stageCode");
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		long current = currentValue == null ? 1 : Math.max(1, currentValue);
		long size = sizeValue == null ? 30 : Math.min(100, Math.max(1, sizeValue));
		if (current > MAX_PAGEABLE_TOTAL + 1) {
			throw new BootBusinessException("日志翻页超过 10000 条，请缩小日期范围或增加筛选条件");
		}
		long offset = (current - 1) * size;
		if (offset >= MAX_PAGEABLE_TOTAL) {
			throw new BootBusinessException("日志翻页超过 10000 条，请缩小日期范围或增加筛选条件");
		}
		long pageLimit = Math.min(size, MAX_PAGEABLE_TOTAL - offset);
		boolean defaultTimelineQuery = !StringUtils.hasText(scopedAdvertiserId) && !StringUtils.hasText(promotionLinkId)
				&& !StringUtils.hasText(mediaId) && !StringUtils.hasText(monitorTaskId) && !StringUtils.hasText(eventId)
				&& !StringUtils.hasText(normalizedStage);
		var query = new OcpxFlowLogSqlMapper.Query(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), normalizedType,
				normalizedStatus, scopedAdvertiserId, promotionLinkId, mediaId, monitorTaskId, eventId, normalizedStage,
				defaultTimelineQuery, pageLimit + 1, offset);
		List<FlowLogViews.Item> fetched = sqlMapper.selectPage(query)
			.stream()
			.map(rs -> new FlowLogViews.Item(rs.id(), rs.flowType(), rs.traceId(), rs.stageCode(), rs.eventId(),
					rs.sourceEventId(), rs.eventCode(), rs.advertiserId(), rs.advertiserName(), rs.promotionLinkId(),
					rs.linkName(), rs.mediaId(), rs.mediaName(), rs.monitorTaskId(), rs.monitorName(),
					rs.resultStatus(), (Integer) rs.partnerHttpStatus(), rs.message(), rs.durationMs(),
					time(rs.eventTime()), time(rs.createTime())))
			.toList();
		boolean hasNext = fetched.size() > pageLimit;
		List<FlowLogViews.Item> records = hasNext ? List.copyOf(fetched.subList(0, (int) pageLimit)) : fetched;
		long totalLowerBound = offset + records.size() + (hasNext ? 1 : 0);
		return new FlowLogViews.Page(records, current, size, totalLowerBound, hasNext);
	}

	public FlowLogViews.RequestDetail requestDetail(String flowLogId) {
		advertiserScope.requireSystemAdministrator();
		if (!StringUtils.hasText(flowLogId) || flowLogId.length() > 32) {
			throw new BootBusinessException("流程日志 ID 无效");
		}
		List<FlowLogViews.RequestDetail> records = sqlMapper.selectDetail(flowLogId.trim()).stream().map(rs -> {
			FlowLogViews.RequestDetail detail = new FlowLogViews.RequestDetail();
			detail.setFlowLogId(rs.flowLogId());
			detail.setFlowType(rs.flowType());
			detail.setTraceId(rs.traceId());
			detail.setEventId(rs.eventId());
			detail.setSourceEventId(rs.sourceEventId());
			detail.setEventCode(rs.eventCode());
			detail.setResultStatus(rs.resultStatus());
			detail.setPartnerHttpStatus((Integer) rs.partnerHttpStatus());
			detail.setMessage(rs.message());
			detail.setResponsePreview(rs.responsePreview());
			detail.setDurationMs(rs.durationMs());
			detail.setEventTime(time(rs.eventTime()));
			detail.setFlowCreatedAt(time(rs.flowCreatedAt()));
			detail.setFeedbackLogId((Long) rs.feedbackLogId());
			detail.setReceivedAt(time(rs.feedbackReceivedAt()));
			detail.setRequestId(rs.reqId());
			detail.setClickId(rs.clkId());
			detail.setImpressionId(rs.impId());
			detail.setUserId(rs.userid());
			detail.setUserType(rs.userType());
			detail.setDeviceOs(rs.deviceOs());
			detail.setOsVersion(rs.osv());
			detail.setModel(rs.model());
			detail.setImei(rs.deviceDid());
			detail.setImeiMd5(rs.deviceImeiMd5());
			detail.setOaid(rs.deviceOaid());
			detail.setOaidMd5(rs.deviceOaidMd5());
			detail.setAndroidId(rs.deviceDpid());
			detail.setAndroidIdMd5(rs.deviceAndroididMd5());
			detail.setIdfa(rs.deviceIdfa());
			detail.setIdfaMd5(rs.deviceIdfaMd5());
			detail.setAaid(rs.aaid());
			detail.setCaid(rs.deviceCaid());
			detail.setCaid1(rs.deviceCaid1());
			detail.setCaid2(rs.deviceCaid2());
			detail.setCaidList(rs.deviceCaidList());
			detail.setLastCaid(rs.lastCaid());
			detail.setPreviousCaid(rs.prevCaid());
			detail.setMac(rs.deviceMac());
			detail.setIp(rs.ip());
			detail.setIpv6(rs.ipv6());
			detail.setUa(rs.ua());
			detail.setCallbackUrl(sanitizedCallbackUrl(rs.callbackUrl()));
			detail.setTimestamp(rs.ts());
			detail.setTimestampMs(rs.tsMs());
			detail.setExternalAdvertiserId(rs.advId());
			detail.setGroupId(rs.groupId());
			detail.setPlanId(rs.planId());
			detail.setCreativeId(rs.creativeId());
			detail.setMediaId(rs.mId());
			detail.setAdvertiserId(rs.aId());
			detail.setMonitorTaskId(rs.tId());
			detail.setPromotionLinkId(rs.lId());
			return detail;
		}).toList();
		if (records.isEmpty()) {
			throw new BootBusinessException(404, "流程日志不存在");
		}
		FlowLogViews.RequestDetail detail = records.get(0);
		detail.setStages(stages(detail.getTraceId(), detail.getFlowLogId()));
		detail.setCoverageStatus(coverage(detail.getTraceId(), detail.getStages()));
		log.info("OCPX_FLOW_DETAIL_VIEWED flowLogId:{} tracePresent:{}", detail.getFlowLogId(),
				StringUtils.hasText(detail.getTraceId()));
		return detail;
	}

	private List<FlowLogViews.Stage> stages(String traceId, String flowLogId) {
		return sqlMapper.selectStages(traceId, flowLogId)
			.stream()
			.map(rs -> new FlowLogViews.Stage(rs.id(), rs.stageCode(), rs.flowType(), rs.eventCode(), rs.resultStatus(),
					rs.requestMethod(), rs.requestEndpoint(), (Integer) rs.partnerHttpStatus(), rs.durationMs(),
					rs.message(), jsonMap(rs.requestParams()), jsonMap(rs.macroAudit()), rs.responsePreview(),
					rs.requestTruncated(), time(rs.eventTime())))
			.toList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> jsonMap(String value) {
		return StringUtils.hasText(value) ? JSON.parseObject(value, Map.class) : Map.of();
	}

	private String coverage(String traceId, List<FlowLogViews.Stage> stages) {
		if (!StringUtils.hasText(traceId))
			return "HISTORICAL_INCOMPLETE";
		boolean hasDetails = stages.stream()
			.anyMatch(stage -> !stage.getRequestParams().isEmpty() || StringUtils.hasText(stage.getResponsePreview()));
		return hasDetails ? "TRACE_AVAILABLE" : "DETAIL_EXPIRED";
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

	private LocalDateTime time(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

}
