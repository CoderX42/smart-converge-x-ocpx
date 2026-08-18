package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskIntegrationRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskIntegrationViews;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.contract.PublicCallbackUrlContract;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.model.OcpxConversionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcpxMonitorTaskIntegrationService {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private static final DateTimeFormatter ATTEMPT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private static final Map<String, Integer> EVENT_TYPES = Map.ofEntries(Map.entry("ACTIVATE", 1),
			Map.entry("EVOKE", 2), Map.entry("LOGIN", 3), Map.entry("REGISTER", 4), Map.entry("PAYMENT", 5),
			Map.entry("CUSTOMER_ACQUISITION", 6), Map.entry("NEXT_STAY", 7), Map.entry("VISITS", 8),
			Map.entry("AUTHENTICATION", 9), Map.entry("ORDER", 10), Map.entry("FINISH_THE_ORDER", 11),
			Map.entry("FIRST_ACTIVATION", 18), Map.entry("FIRST_ORDER", 19));

	private final OcpxMonitorTaskMapper taskMapper;

	private final AdvertiserScope advertiserScope;

	private final JdbcTemplate jdbcTemplate;

	private final OcpxIntegrationCallbackHttpClient httpClient;

	private final OcpxTrackingProperties trackingProperties;

	private final PublicCallbackUrlContract callbackUrlContract = new PublicCallbackUrlContract();

	public MonitorTaskIntegrationViews.Page feedbackRequests(String taskId, LocalDate startDate, LocalDate endDate,
			String requestId, Long currentValue, Long sizeValue) {
		advertiserScope.requireSystemAdministrator();
		requireTask(taskId);
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		LocalDate end = endDate == null ? today : endDate;
		LocalDate start = startDate == null ? end : startDate;
		if (start.isAfter(end) || start.isBefore(end.minusDays(30))) {
			throw new BootBusinessException("联调明细时间范围无效，最多查询 31 天");
		}
		String normalizedRequestId = trim(requestId);
		if (normalizedRequestId != null && normalizedRequestId.length() > 128) {
			throw new BootBusinessException("requestId 长度不能超过 128");
		}
		long current = currentValue == null ? 1 : Math.max(1, currentValue);
		long size = sizeValue == null ? 20 : Math.min(100, Math.max(1, sizeValue));
		StringBuilder where = new StringBuilder(" WHERE t_id=? AND create_time>=? AND create_time<?");
		List<Object> args = new ArrayList<>();
		args.add(taskId);
		args.add(start.atStartOfDay());
		args.add(end.plusDays(1).atStartOfDay());
		if (normalizedRequestId != null) {
			where.append(" AND req_id=?");
			args.add(normalizedRequestId);
		}
		Long totalValue = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_log_feedback" + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size);
		pageArgs.add((current - 1) * size);
		String sql = """
				SELECT id,create_time,req_id,clk_id,userid,user_type,device_os,device_did,device_oaid,
				       device_dpid,device_mac,device_idfa,device_caid,device_caid1,ip,ua,callback_url,ts,ts_ms,
				       adv_id,group_id,plan_id,creative_id,m_id,a_id,t_id,l_id
				FROM scx_ocpx_log_feedback
				""" + where + " ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?";
		List<MonitorTaskIntegrationViews.FeedbackItem> records = jdbcTemplate.query(sql, (rs, rowNum) -> {
			String callbackUrl = decodedCallbackUrl(rs.getString(17));
			boolean callbackAvailable = StringUtils.hasText(callbackUrl);
			return new MonitorTaskIntegrationViews.FeedbackItem(rs.getLong(1), time(rs.getTimestamp(2)),
					rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
					rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
					rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16), callbackUrl,
					rs.getString(18), rs.getString(19), rs.getString(20), rs.getString(21), rs.getString(22),
					rs.getString(23), rs.getString(24), rs.getString(25), rs.getString(26), rs.getString(27),
					callbackAvailable, callbackAvailable && !isInternalTestCallback(callbackUrl));
		}, pageArgs.toArray());
		return new MonitorTaskIntegrationViews.Page(records, current, size, totalValue == null ? 0 : totalValue);
	}

	public MonitorTaskIntegrationViews.CallbackResult sendCallback(String taskId,
			MonitorTaskIntegrationRequests.Callback request) {
		advertiserScope.requireSystemAdministrator();
		OcpxMonitorTask task = requireTask(taskId);
		FeedbackSnapshot feedback = feedback(taskId, request.getFeedbackLogId());
		if (!same(task.getAdvertiserId(), feedback.advertiserId())
				|| !same(task.getPromotionLinkId(), feedback.promotionLinkId())
				|| !same(task.getMediaId(), feedback.mediaId())) {
			throw new BootBusinessException(HttpStatus.CONFLICT.value(), "请求明细与当前监测任务关系不一致");
		}
		if (!StringUtils.hasText(feedback.callbackUrl())) {
			throw new BootBusinessException("该下游请求没有 callback_url，无法回传");
		}
		String callbackUrl = decodedCallbackUrl(feedback.callbackUrl());
		if (isInternalTestCallback(callbackUrl)) {
			throw new BootBusinessException("SCX 完整测试 Callback 由 Engine 自动签名投递，不能通过媒体联调手工发送");
		}
		String eventCode = request.getEventCode().trim().toUpperCase(Locale.ROOT);
		if (!OcpxConversionEvent.supports(eventCode) || !EVENT_TYPES.containsKey(eventCode)) {
			throw new BootBusinessException("Callback 事件不受支持");
		}
		Integer eventType = EVENT_TYPES.get(eventCode);
		String attemptId = nextAttemptId();
		LocalDateTime sentAt = LocalDateTime.now(BUSINESS_ZONE);
		OcpxIntegrationCallbackHttpClient.Response response;
		String resultStatus;
		String message;
		try {
			String publicUrl = callbackUrlContract.requirePublicHttpUrl(callbackUrl);
			String requestUrl = callbackUrlContract.appendCallbackFields(publicUrl, eventType,
					request.getCurrencyAmount());
			response = httpClient.get(URI.create(requestUrl));
			boolean success = response.errorCode() == null && response.statusCode() != null
					&& response.statusCode() >= 200 && response.statusCode() < 300;
			resultStatus = success ? "SUCCESS" : "FAILED";
			message = success ? "INTEGRATION_CALLBACK_RESPONDED"
					: response.errorCode() == null ? "INTEGRATION_CALLBACK_HTTP_FAILED" : response.errorCode();
		}
		catch (IllegalArgumentException exception) {
			response = new OcpxIntegrationCallbackHttpClient.Response(null, null, null, 0L);
			resultStatus = "FAILED";
			message = "INTEGRATION_CALLBACK_URL_REJECTED";
		}
		boolean audited = writeAudit(attemptId, feedback, eventCode, request.getCurrencyAmount(), resultStatus,
				response.statusCode(), message, response.durationMs(), sentAt);
		if (!audited) {
			message += "_AUDIT_FAILED";
		}
		return new MonitorTaskIntegrationViews.CallbackResult(attemptId, feedback.requestId(), eventCode, eventType,
				request.getCurrencyAmount(), resultStatus, response.statusCode(), message, response.durationMs(),
				response.body(), sentAt);
	}

	private OcpxMonitorTask requireTask(String taskId) {
		OcpxMonitorTask task = taskMapper.selectById(taskId);
		if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "监测任务不存在");
		}
		return task;
	}

	private FeedbackSnapshot feedback(String taskId, Long feedbackLogId) {
		List<FeedbackSnapshot> records = jdbcTemplate.query("""
				SELECT req_id,callback_url,a_id,l_id,m_id,t_id
				FROM scx_ocpx_log_feedback
				WHERE id=? AND t_id=? LIMIT 1
				""", (rs, rowNum) -> new FeedbackSnapshot(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getString(4), rs.getString(5), rs.getString(6)), feedbackLogId, taskId);
		if (records.isEmpty()) {
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "下游请求明细不存在或已过期");
		}
		return records.get(0);
	}

	private boolean writeAudit(String attemptId, FeedbackSnapshot feedback, String eventCode, BigDecimal amount,
			String resultStatus, Integer httpStatus, String message, long durationMs, LocalDateTime eventTime) {
		try {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log
					(id,flow_type,event_id,source_event_id,event_code,payload_hash,advertiser_id,
					 promotion_link_id,media_id,monitor_task_id,result_status,partner_http_status,
					 message,duration_ms,event_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", uuid(), "CALLBACK", attemptId, limit(feedback.requestId(), 128), eventCode,
					fingerprint(feedback, eventCode, amount), feedback.advertiserId(), feedback.promotionLinkId(),
					feedback.mediaId(), feedback.monitorTaskId(), resultStatus, httpStatus, limit(message, 500),
					durationMs, eventTime);
			return true;
		}
		catch (RuntimeException exception) {
			log.warn("Unable to persist media integration callback audit, attemptId:{}", attemptId);
			return false;
		}
	}

	private String fingerprint(FeedbackSnapshot feedback, String eventCode, BigDecimal amount) {
		String value = String.join("|", safe(feedback.advertiserId()), safe(feedback.promotionLinkId()),
				safe(feedback.mediaId()), safe(feedback.monitorTaskId()), safe(feedback.requestId()), eventCode,
				amount == null ? "" : amount.toPlainString());
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("Unable to hash integration callback audit", exception);
		}
	}

	private String decodedCallbackUrl(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private boolean isInternalTestCallback(String callbackUrl) {
		if (!StringUtils.hasText(callbackUrl)) {
			return false;
		}
		try {
			URI base = URI.create(trackingProperties.getEngineBaseUrl().trim().replaceAll("/+$", "")).normalize();
			URI callback = URI.create(callbackUrl.trim()).normalize();
			String basePath = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
			return sameIgnoreCase(base.getScheme(), callback.getScheme())
					&& sameIgnoreCase(base.getHost(), callback.getHost())
					&& effectivePort(base) == effectivePort(callback)
					&& (basePath + "/test-callback").equals(callback.getPath());
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private int effectivePort(URI uri) {
		if (uri.getPort() >= 0) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private boolean sameIgnoreCase(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	private String nextAttemptId() {
		String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		return "SCX-CB-UAT-" + LocalDateTime.now(BUSINESS_ZONE).format(ATTEMPT_TIME) + "-" + random;
	}

	private LocalDateTime time(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private boolean same(String left, String right) {
		return left != null && left.equals(right);
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String limit(String value, int length) {
		if (value == null || value.length() <= length) {
			return value;
		}
		return value.substring(0, length);
	}

	private String uuid() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private record FeedbackSnapshot(String requestId, String callbackUrl, String advertiserId, String promotionLinkId,
			String mediaId, String monitorTaskId) {

	}

}
