package com.yzytech.scx.ocpx.callback.service;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 管理完整测试的受控回调令牌，并记录正式下游投递产生的最后一跳。 */
@Service
public class TestCallbackCollectorService {

	private static final Pattern TEST_ID = Pattern.compile("SCX-UAT-\\d{14}-[0-9a-f]{12}");

	private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");

	private static final Set<String> CALLBACK_KEYS = Set.of("request_id", "token", "mId", "tId", "aId", "lId",
			"event_type");

	private final JdbcTemplate jdbcTemplate;

	private final String callbackBaseUrl;

	private final String callbackSigningKey;

	public TestCallbackCollectorService(JdbcTemplate jdbcTemplate, @Value("${domain.name}") String callbackBaseUrl,
			@Value("${app.callback-aes-key}") String callbackSigningKey) {
		this.jdbcTemplate = jdbcTemplate;
		this.callbackBaseUrl = callbackBaseUrl;
		this.callbackSigningKey = callbackSigningKey;
	}

	/** 只为固定测试接收地址追加 Engine 正式投递签名；普通媒体地址保持不变。 */
	public String signDeliveryIfTestCallback(String callbackUrl, Integer eventType, BigDecimal currencyAmount) {
		URI callback = URI.create(callbackUrl);
		URI base = URI.create(callbackBaseUrl.replaceAll("/+$", ""));
		String expectedPath = (base.getPath() == null ? "" : base.getPath()) + "/test-callback";
		if (!sameAuthority(base, callback) || !expectedPath.equals(callback.getPath())) {
			return callbackUrl;
		}
		Map<String, String> parameters = query(callback.getRawQuery());
		Set<String> expectedKeys = currencyAmount == null ? CALLBACK_KEYS
				: Set.of("request_id", "token", "mId", "tId", "aId", "lId", "event_type", "currency_amount");
		if (!parameters.keySet().equals(expectedKeys) || eventType == null
				|| !eventType.toString().equals(parameters.get("event_type")) || (currencyAmount != null
						&& !currencyAmount.toPlainString().equals(parameters.get("currency_amount")))) {
			throw new IllegalArgumentException("invalid fixed test callback delivery URL");
		}
		validate(parameters.get("request_id"), parameters.get("token"), parameters.get("mId"), parameters.get("tId"),
				parameters.get("aId"), parameters.get("lId"), eventType, currencyAmount);
		String signature = deliverySignature(parameters.get("request_id"), parameters.get("token"),
				parameters.get("mId"), parameters.get("tId"), parameters.get("aId"), parameters.get("lId"), eventType,
				currencyAmount);
		return callbackUrl + "&delivery_signature=" + signature;
	}

	/** 在测试 Feedback 形成事实后登记令牌指纹，供最后一跳校验。 */
	public boolean arm(FeedbackReq request) {
		if (request == null || !isTestId(request.getReqId())) {
			return false;
		}
		if (request.getEvent() == null || !"CLK".equals(request.getEvent().getKey())) {
			throw new IllegalArgumentException("test callback can only be armed by CLK Feedback");
		}
		if (!StringUtils.hasText(request.getCallbackUrl())) {
			throw new IllegalArgumentException("test callback URL is missing");
		}
		URI callback = URI.create(URLDecoder.decode(request.getCallbackUrl(), StandardCharsets.UTF_8));
		URI base = URI.create(callbackBaseUrl.replaceAll("/+$", ""));
		String expectedPath = (base.getPath() == null ? "" : base.getPath()) + "/test-callback";
		if (!sameAuthority(base, callback) || !expectedPath.equals(callback.getPath())
				|| callback.getFragment() != null) {
			throw new IllegalArgumentException("test callback URL is outside the fixed Engine route");
		}
		Map<String, String> query = query(callback.getRawQuery());
		if (!query.keySet().equals(Set.of("request_id", "token", "mId", "tId", "aId", "lId"))) {
			throw new IllegalArgumentException("invalid fixed test callback query");
		}
		String token = query.get("token");
		validate(request.getReqId(), token, request.getMId(), request.getTId(), request.getAId(), request.getLId(),
				null, null);
		if (!request.getReqId().equals(query.get("request_id")) || !request.getMId().equals(query.get("mId"))
				|| !request.getTId().equals(query.get("tId")) || !request.getAId().equals(query.get("aId"))
				|| !request.getLId().equals(query.get("lId"))) {
			throw new IllegalArgumentException("test callback route does not match Feedback");
		}
		String payloadHash = armedFingerprint(request.getReqId(), token, request.getMId(), request.getTId(),
				request.getAId(), request.getLId());
		String id = fingerprint(request.getReqId(), "TEST_CALLBACK_ARMED").substring(0, 32);
		try {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log
					(id,flow_type,event_id,source_event_id,event_code,payload_hash,advertiser_id,
					 promotion_link_id,media_id,monitor_task_id,result_status,partner_http_status,
					 message,duration_ms,event_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", id, "CALLBACK", request.getReqId(), request.getReqId(), "CLK", payloadHash, request.getAId(),
					request.getLId(), request.getMId(), request.getTId(), "SUCCESS", null, "TEST_CALLBACK_ARMED", 0L,
					LocalDateTime.now());
			return true;
		}
		catch (DuplicateKeyException exception) {
			return false;
		}
	}

	/**
	 * 只有令牌已由原测试 Feedback 登记，且同一路由、同一事件已经形成 PASSED Callback 事实时，才记录测试接收成功。
	 * @return {@code true} 首次记录；{@code false} 幂等重复
	 */
	public boolean record(String requestId, String token, String mediaId, String monitorTaskId, String advertiserId,
			String promotionLinkId, Integer eventType, BigDecimal currencyAmount, String deliverySignature) {
		validate(requestId, token, mediaId, monitorTaskId, advertiserId, promotionLinkId, eventType, currencyAmount);
		if (eventType == null) {
			throw new IllegalArgumentException("invalid test callback event");
		}
		String expectedSignature = deliverySignature(requestId, token, mediaId, monitorTaskId, advertiserId,
				promotionLinkId, eventType, currencyAmount);
		if (!StringUtils.hasText(deliverySignature)
				|| !MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.US_ASCII),
						deliverySignature.getBytes(StandardCharsets.US_ASCII))) {
			throw new IllegalArgumentException("invalid test callback delivery signature");
		}
		ActionTypeEnum actionType = ActionTypeEnum.getActionTypeEnum(eventType);
		String armedHash = armedFingerprint(requestId, token, mediaId, monitorTaskId, advertiserId, promotionLinkId);
		Long armed = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_flow_log
				WHERE event_id=? AND media_id=? AND monitor_task_id=? AND advertiser_id=?
				  AND promotion_link_id=? AND flow_type='CALLBACK' AND message='TEST_CALLBACK_ARMED'
				  AND payload_hash=?
				""", Long.class, requestId, mediaId, monitorTaskId, advertiserId, promotionLinkId, armedHash);
		Long passed = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_event_fact
				WHERE request_id=? AND media_id=? AND monitor_task_id=? AND advertiser_id=?
				  AND promotion_link_id=? AND flow_type='CALLBACK' AND event_code=?
				  AND deduction_decision='PASSED'
				""", Long.class, requestId, mediaId, monitorTaskId, advertiserId, promotionLinkId, actionType.name());
		if (armed == null || armed != 1L || passed == null || passed != 1L) {
			throw new CallbackNotReadyException();
		}
		String payloadHash = fingerprint(requestId, token, mediaId, monitorTaskId, advertiserId, promotionLinkId,
				actionType.name(), currencyAmount);
		String id = fingerprint(requestId, actionType.name(), "TEST_CALLBACK_RECEIVED").substring(0, 32);
		try {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_flow_log
					(id,flow_type,event_id,source_event_id,event_code,payload_hash,advertiser_id,
					 promotion_link_id,media_id,monitor_task_id,result_status,partner_http_status,
					 message,duration_ms,event_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", id, "CALLBACK", requestId, requestId, actionType.name(), payloadHash, advertiserId,
					promotionLinkId, mediaId, monitorTaskId, "SUCCESS", null, "TEST_CALLBACK_RECEIVED", 0L,
					LocalDateTime.now());
			return true;
		}
		catch (DuplicateKeyException exception) {
			return false;
		}
	}

	private void validate(String requestId, String token, String mediaId, String monitorTaskId, String advertiserId,
			String promotionLinkId, Integer eventType, BigDecimal currencyAmount) {
		if (!isTestId(requestId) || token == null || !TOKEN.matcher(token).matches()) {
			throw new IllegalArgumentException("invalid test callback identity");
		}
		for (String dimension : new String[] { mediaId, monitorTaskId, advertiserId, promotionLinkId }) {
			if (!StringUtils.hasText(dimension) || dimension.length() > 32
					|| dimension.chars().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("invalid test callback route");
			}
		}
		if (eventType != null && ActionTypeEnum.getActionTypeEnum(eventType) == null) {
			throw new IllegalArgumentException("invalid test callback event");
		}
		if (currencyAmount != null
				&& (currencyAmount.signum() < 0 || currencyAmount.scale() > 6 || currencyAmount.precision() > 26)) {
			throw new IllegalArgumentException("invalid test callback amount");
		}
	}

	private boolean isTestId(String requestId) {
		return requestId != null && TEST_ID.matcher(requestId).matches();
	}

	private boolean sameAuthority(URI left, URI right) {
		return left.getScheme() != null && left.getScheme().equalsIgnoreCase(right.getScheme())
				&& left.getHost() != null && left.getHost().equalsIgnoreCase(right.getHost())
				&& left.getPort() == right.getPort() && right.getUserInfo() == null;
	}

	private Map<String, String> query(String rawQuery) {
		Map<String, String> result = new HashMap<>();
		if (!StringUtils.hasText(rawQuery)) {
			return result;
		}
		for (String pair : rawQuery.split("&")) {
			String[] parts = pair.split("=", 2);
			String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
			String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
			if (result.putIfAbsent(key, value) != null) {
				throw new IllegalArgumentException("duplicate test callback query parameter");
			}
		}
		return result;
	}

	private String armedFingerprint(String requestId, String token, String mediaId, String monitorTaskId,
			String advertiserId, String promotionLinkId) {
		return fingerprint(requestId, token, mediaId, monitorTaskId, advertiserId, promotionLinkId,
				"TEST_CALLBACK_ARMED");
	}

	private String deliverySignature(String requestId, String token, String mediaId, String monitorTaskId,
			String advertiserId, String promotionLinkId, Integer eventType, BigDecimal currencyAmount) {
		String value = String.join("|", safe(requestId), safe(token), safe(mediaId), safe(monitorTaskId),
				safe(advertiserId), safe(promotionLinkId), eventType == null ? "" : eventType.toString(),
				currencyAmount == null ? "" : currencyAmount.toPlainString());
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			byte[] separatedKey = MessageDigest.getInstance("SHA-256")
				.digest(("SCX-TEST-CALLBACK|" + callbackSigningKey).getBytes(StandardCharsets.UTF_8));
			mac.init(new SecretKeySpec(separatedKey, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("Unable to sign test callback delivery", exception);
		}
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String fingerprint(Object... values) {
		StringBuilder value = new StringBuilder();
		for (Object item : values) {
			if (!value.isEmpty()) {
				value.append('|');
			}
			value.append(item == null ? "" : item);
		}
		try {
			return HexFormat.of()
				.formatHex(
						MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("Unable to hash test callback audit", exception);
		}
	}

	public static final class CallbackNotReadyException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private CallbackNotReadyException() {
			super("test callback fact is not ready");
		}

	}

}
