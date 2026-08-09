package com.yzytech.scx.ocpx.runtime.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.AdapterDebugSupport;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.contract.UpstreamAdapter;
import com.yzytech.scx.common.log.appender.BoundedSanitizedLogAppender;
import com.yzytech.scx.ocpx.runtime.adapter.AdapterRegistry;
import com.yzytech.scx.ocpx.runtime.callback.CallbackIngressService;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.config.RouteConfigurationRepository;
import com.yzytech.scx.ocpx.runtime.ingress.FeedbackIngressService;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.model.IngressResult;
import com.yzytech.scx.ocpx.runtime.persistence.OcpxPersistenceRepository;
import com.yzytech.scx.ocpx.runtime.security.CallbackTokenService;
import com.yzytech.scx.ocpx.runtime.util.OcpxHashing;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class InternalApiDebugService {

	private static final Logger LOGGER = LoggerFactory.getLogger(InternalApiDebugService.class);

	private final StringRedisTemplate redisTemplate;

	private final ObjectMapper objectMapper;

	private final JdbcTemplate jdbcTemplate;

	private final OcpxRuntimeProperties properties;

	private final RouteConfigurationRepository routeRepository;

	private final FeedbackIngressService feedbackIngressService;

	private final CallbackIngressService callbackIngressService;

	private final OcpxPersistenceRepository persistenceRepository;

	private final CallbackTokenService callbackTokenService;

	private final AdapterRegistry adapterRegistry;

	public InternalApiDebugService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
			JdbcTemplate jdbcTemplate, OcpxRuntimeProperties properties, RouteConfigurationRepository routeRepository,
			FeedbackIngressService feedbackIngressService, CallbackIngressService callbackIngressService,
			OcpxPersistenceRepository persistenceRepository, CallbackTokenService callbackTokenService,
			AdapterRegistry adapterRegistry) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
		this.routeRepository = routeRepository;
		this.feedbackIngressService = feedbackIngressService;
		this.callbackIngressService = callbackIngressService;
		this.persistenceRepository = persistenceRepository;
		this.callbackTokenService = callbackTokenService;
		this.adapterRegistry = adapterRegistry;
	}

	public void authenticate(String method, String path, String body, HttpHeaders headers) {
		String timestamp = required(headers, "X-SCX-Debug-Timestamp");
		String nonce = required(headers, "X-SCX-Debug-Nonce");
		String key = required(headers, "X-SCX-Debug-Key");
		String signature = required(headers, "X-SCX-Debug-Sign");
		long sentAt;
		try {
			sentAt = Long.parseLong(timestamp);
		}
		catch (NumberFormatException error) {
			throw new IllegalArgumentException("invalid internal debug timestamp");
		}
		if (Math.abs(System.currentTimeMillis() - sentAt) > 30_000) {
			throw new IllegalArgumentException("internal debug grant expired");
		}
		String stored = redisTemplate.opsForValue().getAndDelete("scx:api-debug:internal:" + nonce);
		String bodyHash = sha256(body == null ? "" : body);
		String expectedGrant = sha256(key) + "|" + method + "|" + path + "|" + bodyHash + "|" + timestamp;
		if (!StringUtils.hasText(stored) || !MessageDigest.isEqual(bytes(stored), bytes(expectedGrant))) {
			throw new IllegalArgumentException("invalid or replayed internal debug grant");
		}
		String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
		if (!OcpxHashing.constantTimeEquals(signature, hmac(key, canonical))) {
			throw new IllegalArgumentException("invalid internal debug signature");
		}
	}

	public Mono<Accepted> execute(ExecuteRequest request) {
		if (!properties.isDebugExecutionEnabled()) {
			return Mono.error(new IllegalArgumentException("engine debug execution is disabled"));
		}
		if (request == null || !StringUtils.hasText(request.executionId())
				|| !StringUtils.hasText(request.monitorId())) {
			return Mono.error(new IllegalArgumentException("executionId and monitorId are required"));
		}
		RouteSnapshot route = routeRepository.findPublishedById(request.monitorId())
			.orElseThrow(() -> new IllegalArgumentException("published route not found"));
		if (request.routeVersion() == null || route.routeVersion() != request.routeVersion()) {
			return Mono.error(new IllegalArgumentException("published route version changed after preflight"));
		}
		appendLog(request.executionId(), "ENGINE_ACCEPTED", "引擎已验证一次性内部授权并冻结已发布路由");
		if ("DOWNSTREAM_TO_UPSTREAM".equals(request.scenario())) {
			return executeFeedback(request, route);
		}
		if ("UPSTREAM_TO_DOWNSTREAM".equals(request.scenario())) {
			return executeCallback(request, route);
		}
		return Mono.error(new IllegalArgumentException("unsupported debug scenario"));
	}

	private Mono<Accepted> executeFeedback(ExecuteRequest request, RouteSnapshot route) {
		try {
			UpstreamAdapter adapter = adapterRegistry.requireUpstream(route.upstreamAdapterCode());
			if (!(adapter instanceof AdapterDebugSupport debugSupport)) {
				throw new IllegalArgumentException(
						"selected upstream adapter does not support integrated debug synthesis");
			}
			FeedbackReqV1 feedback = StringUtils.hasText(request.body())
					? objectMapper.readValue(request.body(), FeedbackReqV1.class)
					: debugSupport.sampleFeedback(route, request.executionId());
			feedback.setMonitorId(route.routeId());
			feedback.setChannelId(route.channelCode());
			if (!StringUtils.hasText(feedback.getEventId())) {
				feedback.setEventId("debug-" + request.executionId());
			}
			if (feedback.getEventTime() == null) {
				feedback.setEventTime(Instant.now().getEpochSecond());
			}
			String rawBody = objectMapper.writeValueAsString(feedback);
			String path = basePath() + "/open-api/ocpx/v1/channels/" + route.channelCode() + "/feedback";
			HttpHeaders headers = signedFeedbackHeaders(route, path, rawBody);
			appendLog(request.executionId(), "FEEDBACK_INGRESS", "进入真实 Feedback 鉴权、幂等与 Redis Stream 链路");
			return feedbackIngressService
				.accept(route.channelCode(), "POST", path, rawBody, headers, request.executionId())
				.map(result -> {
					appendLog(request.executionId(), "FEEDBACK_ENQUEUED", "Feedback 已由可靠入口接受");
					return new Accepted(request.executionId(), "WAITING_ASYNC", result.eventId());
				});
		}
		catch (Exception error) {
			return fail(request.executionId(), error);
		}
	}

	private Mono<Accepted> executeCallback(ExecuteRequest request, RouteSnapshot route) {
		try {
			JsonNode body = StringUtils.hasText(request.body()) ? objectMapper.readTree(request.body())
					: objectMapper.createObjectNode();
			String callbackRef = text(body, "callback_ref");
			if (!StringUtils.hasText(callbackRef)) {
				callbackRef = latestCallbackRef(route.routeId());
			}
			CallbackContext context = persistenceRepository.requireCallbackContext(callbackRef);
			if (!route.routeId().equals(context.route().routeId())) {
				throw new IllegalArgumentException("callback reference does not belong to selected route");
			}
			UpstreamAdapter adapter = adapterRegistry.requireUpstream(route.upstreamAdapterCode());
			if (!(adapter instanceof AdapterDebugSupport debugSupport)) {
				throw new IllegalArgumentException(
						"selected upstream adapter does not support integrated debug synthesis");
			}
			String callbackToken = callbackTokenService.issue(callbackRef);
			Map<String, String> parameters = new LinkedHashMap<>(
					debugSupport.sampleCallback(route, callbackToken, request.executionId()));
			body.fields().forEachRemaining(entry -> {
				if (!"callback_ref".equals(entry.getKey()) && entry.getValue().isValueNode()) {
					parameters.put(entry.getKey(), entry.getValue().asText());
				}
			});
			parameters.put("ext", callbackToken);
			if (StringUtils.hasText(text(body, "event"))) {
				parameters.put("action_type", text(body, "event"));
			}
			if (StringUtils.hasText(text(body, "amount"))) {
				parameters.put("currency_amount", text(body, "amount"));
			}
			appendLog(request.executionId(), "CALLBACK_INGRESS", "进入真实 callback token、扣量和下游投递链路");
			return callbackIngressService.accept(adapter, parameters, request.executionId()).map(result -> {
				appendLog(request.executionId(), "CALLBACK_ACCEPTED", "上游 callback 已完成事实持久化");
				return new Accepted(request.executionId(), "WAITING_ASYNC", result.eventId());
			});
		}
		catch (Exception error) {
			return fail(request.executionId(), error);
		}
	}

	private Mono<Accepted> fail(String executionId, Exception error) {
		String message = sanitize(
				error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "failed"));
		jdbcTemplate.update(
				"UPDATE scx_api_debug_execution SET status='FAILED',error_message=?,update_time=NOW(3) WHERE id=?",
				truncate(message, 1000), executionId);
		appendLog(executionId, "ENGINE_FAILED", message);
		return Mono.error(new IllegalArgumentException(message));
	}

	public RecentLogs recentLogs(String level, String logger, String keyword, long cursor) {
		BoundedSanitizedLogAppender.QueryResult result = BoundedSanitizedLogAppender.query(level, logger, keyword,
				cursor);
		List<RecentLog> rows = result.records()
			.stream()
			.map(row -> new RecentLog(row.sequence(), "ENGINE", row.timestamp(), row.level(), row.logger(),
					row.message()))
			.toList();
		return new RecentLogs(rows, result.nextCursor(), result.truncated());
	}

	private HttpHeaders signedFeedbackHeaders(RouteSnapshot route, String path, String body) {
		HttpHeaders headers = new HttpHeaders();
		if ("NONE".equalsIgnoreCase(route.channelAuthType())) {
			return headers;
		}
		String timestamp = Long.toString(System.currentTimeMillis());
		String nonce = UUID.randomUUID().toString().replace("-", "");
		String canonical = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + sha256(body);
		headers.set("X-Client-Id", route.channelCode());
		headers.set("X-Key-Version", Integer.toString(route.channelKeyVersion()));
		headers.set("X-Timestamp", timestamp);
		headers.set("X-Nonce", nonce);
		headers.set("X-Sign", OcpxHashing.hmacSha256(route.channelSecret(), canonical));
		return headers;
	}

	private String latestCallbackRef(String monitorId) {
		List<String> rows = jdbcTemplate.query("""
				SELECT r.id FROM scx_ocpx_callback_ref r
				JOIN scx_ocpx_event e ON e.event_id=r.feedback_event_id
				WHERE e.monitor_id=? AND r.expires_at>NOW(3) ORDER BY r.create_time DESC LIMIT 1
				""", (rs, rowNum) -> rs.getString(1), monitorId);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("selected route has no active callback reference");
		}
		return rows.get(0);
	}

	private void appendLog(String executionId, String phase, String message) {
		for (int attempt = 0; attempt < 4; attempt++) {
			Long sequence = jdbcTemplate.queryForObject(
					"SELECT COALESCE(MAX(sequence_no),0)+1 FROM scx_api_debug_execution_log WHERE execution_id=?",
					Long.class, executionId);
			try {
				jdbcTemplate.update("""
						INSERT INTO scx_api_debug_execution_log
						(id,execution_id,sequence_no,source,level,phase,message,detail_json,create_time)
						VALUES (?,?,?,'ENGINE','INFO',?,?,NULL,NOW(3))
						""", id(), executionId, sequence == null ? 1 : sequence, phase,
						truncate(sanitize(message), 2000));
				break;
			}
			catch (DuplicateKeyException error) {
				if (attempt == 3) {
					throw error;
				}
			}
		}
		try (MDC.MDCCloseable ignored = MDC.putCloseable("debugExecutionId", executionId)) {
			LOGGER.info("API debug phase={} message={}", phase, truncate(sanitize(message), 2000));
		}
	}

	private String basePath() {
		String path = URI.create(properties.getPublicBaseUrl()).getPath();
		return path == null || path.equals("/") ? "" : path.replaceAll("/$", "");
	}

	private String required(HttpHeaders headers, String name) {
		String value = headers.getFirst(name);
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(name + " is required");
		}
		return value;
	}

	private static String text(JsonNode body, String name) {
		JsonNode value = body == null ? null : body.get(name);
		return value == null || value.isNull() ? null : value.asText();
	}

	private static String sanitize(String value) {
		return value == null ? null : truncate(BoundedSanitizedLogAppender.sanitizeText(value), 4000);
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	private static String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String sha256(String value) {
		return HexFormat.of().formatHex(digest(bytes(value)));
	}

	private static byte[] digest(byte[] value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value);
		}
		catch (Exception error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	private static String hmac(String key, String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(bytes(key), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(bytes(value)));
		}
		catch (Exception error) {
			throw new IllegalStateException("HMAC-SHA256 unavailable", error);
		}
	}

	public record ExecuteRequest(String executionId, String scenario, String monitorId, Long routeVersion,
			String method, String body, Map<String, String> headers) {
	}

	public record Accepted(String executionId, String status, String eventId) {
	}

	public record RecentLog(long sequence, String source, String timestamp, String level, String logger,
			String message) {
	}

	public record RecentLogs(List<RecentLog> records, long nextCursor, boolean truncated) {
	}

}
