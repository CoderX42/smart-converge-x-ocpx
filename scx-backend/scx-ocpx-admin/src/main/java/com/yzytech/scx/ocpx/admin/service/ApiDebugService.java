package com.yzytech.scx.ocpx.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.config.ApiDebugProperties;
import com.yzytech.scx.ocpx.admin.dto.ApiDebugRequests;
import com.yzytech.scx.ocpx.admin.dto.ApiDebugViews;
import com.yzytech.scx.common.log.appender.BoundedSanitizedLogAppender;
import com.yzytech.scx.common.security.util.SecurityUtils;
import jakarta.annotation.PreDestroy;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class ApiDebugService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiDebugService.class);

	private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

	private static final Set<String> FORBIDDEN_HEADERS = Set.of("host", "content-length", "connection",
			"transfer-encoding", "upgrade", "proxy-authorization", "proxy-connection");

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final StringRedisTemplate redisTemplate;

	private final ApiDebugProperties properties;

	private final OcpxAccessScope accessScope;

	private final ExecutorService executor = Executors.newFixedThreadPool(20);

	private final Semaphore globalConcurrency = new Semaphore(20);

	private final Map<String, Semaphore> userConcurrency = new ConcurrentHashMap<>();

	private final Map<String, Deque<Long>> rateWindows = new ConcurrentHashMap<>();

	private final OkHttpClient httpClient;

	private final OkHttpClient internalClient;

	public ApiDebugService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, StringRedisTemplate redisTemplate,
			ApiDebugProperties properties, OcpxAccessScope accessScope) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.accessScope = accessScope;
		Dns safeDns = hostname -> validateAddresses(hostname);
		this.httpClient = new OkHttpClient.Builder().dns(safeDns)
			.proxy(Proxy.NO_PROXY)
			.followRedirects(false)
			.followSslRedirects(false)
			.connectTimeout(Duration.ofSeconds(10))
			.readTimeout(Duration.ofSeconds(10))
			.writeTimeout(Duration.ofSeconds(10))
			.build();
		this.internalClient = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
			.followRedirects(false)
			.followSslRedirects(false)
			.connectTimeout(Duration.ofSeconds(10))
			.readTimeout(Duration.ofSeconds(10))
			.writeTimeout(Duration.ofSeconds(10))
			.build();
	}

	public List<ApiDebugViews.Target> targets(String keyword) {
		String search = StringUtils.hasText(keyword) ? "%" + keyword.trim() + "%" : "%";
		TargetScope scope = targetScope(OcpxAccessScope.PermissionLevel.VIEW);
		List<Object> args = new ArrayList<>(List.of(search, search, search, search));
		args.addAll(scope.args());
		return jdbcTemplate.query(
				"""
						SELECT m.id,m.monitor_name,m.config_version,m.route_status,c.id,c.channel_name,c.channel_code,c.adapter_code,
						       a.id,a.advertiser_name,a.adapter_code,m.published_upstream_feedback_url,m.downstream_callback_url,
						       m.billing_mode,m.unit_price
						FROM scx_ocpx_monitor_link m
						JOIN scx_ocpx_channel c ON c.id=m.channel_id AND c.deleted=0 AND c.status='ACTIVE'
						JOIN scx_ocpx_advertiser a ON a.id=m.advertiser_id AND a.deleted=0 AND a.status='ACTIVE'
						WHERE m.deleted=0 AND m.route_status='PUBLISHED'
						  AND (m.id LIKE ? OR m.monitor_name LIKE ? OR c.channel_name LIKE ? OR a.advertiser_name LIKE ?)
						"""
						+ scope.clause() + " ORDER BY m.update_time DESC LIMIT 200",
				(rs, rowNum) -> new ApiDebugViews.Target(rs.getString(1), rs.getString(2), rs.getLong(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getString(9), rs.getString(10), rs.getString(11), host(rs.getString(12)),
						host(rs.getString(13)), rs.getString(14), rs.getBigDecimal(15)),
				args.toArray());
	}

	@Transactional
	public ApiDebugViews.Preflight preflight(ApiDebugRequests.Preflight request) {
		ensureEnabled();
		validateRequest(request);
		ApiDebugViews.Target target = null;
		boolean billable = false;
		String requestUrl = request.url();
		String risk;
		String effects;
		Long routeVersion = null;
		if ("INTEGRATED".equals(request.mode())) {
			if (!properties.isFullChainEnabled()) {
				throw new IllegalArgumentException("完整链路调试已停用");
			}
			target = requireTarget(request.monitorId(), OcpxAccessScope.PermissionLevel.OPERATE);
			routeVersion = target.routeVersion();
			billable = isBillable(target.billingMode(), request.scenario(), request.body());
			requestUrl = "DOWNSTREAM_TO_UPSTREAM".equals(request.scenario()) ? target.upstreamTargetHost()
					: target.downstreamTargetHost();
			if (!StringUtils.hasText(requestUrl)) {
				throw new IllegalArgumentException("所选路由未配置该方向的目标地址");
			}
			risk = billable ? "生产完整链路将真实扣费并创建不可变账务流水" : "完整链路将写入真实事件并触发外部投递";
			effects = "事件、幂等、队列、投递、扣量与审计按正式链路执行；调试数据不进入常规效果报表";
		}
		else {
			validatePublicHttps(request.url());
			risk = "自定义请求将从管理端服务器访问公网目标";
			effects = "不写入 OCPX 业务事实；请求和文本响应按明文进入七天历史";
		}
		String id = id();
		String phrase = billable ? "REAL-BILLING:" + id : "EXECUTE:" + id;
		String userId = SecurityUtils.getUserId();
		jdbcTemplate.update(
				"""
						INSERT INTO scx_api_debug_execution
						(id,draft_id,mode,scenario,status,monitor_id,route_version,request_method,request_url,request_headers,
						 body_type,request_body,timeout_seconds,risk_summary,side_effect_summary,created_by,create_time,update_time,expires_at)
						VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3),DATE_ADD(NOW(3),INTERVAL 7 DAY))
						""",
				id, id, request.mode(), request.scenario(), "PREFLIGHT", request.monitorId(), routeVersion,
				request.method(), requestUrl, json(request.safeHeaders()), request.bodyType(), request.body(),
				request.effectiveTimeoutSeconds(), risk, effects, userId);
		appendLog(id, "MANAGEMENT", "INFO", "PREFLIGHT", "服务端预检完成",
				Map.of("billable", billable, "mode", request.mode()));
		return new ApiDebugViews.Preflight(id, phrase, billable, properties.getEnvironment(), risk, effects, target,
				LocalDateTime.now().plusMinutes(10));
	}

	public ApiDebugViews.Authorization authorize(ApiDebugRequests.Authorize request, String sessionToken, String ip) {
		ensureEnabled();
		String userId = SecurityUtils.getUserId();
		List<String> passwordHashes = jdbcTemplate.query(
				"SELECT password FROM sys_user WHERE id=? AND del_flag='0' LIMIT 1", (rs, rowNum) -> rs.getString(1),
				userId);
		if (passwordHashes.isEmpty() || !StringUtils.hasText(passwordHashes.get(0))
				|| !BCrypt.checkpw(request.password(), passwordHashes.get(0))) {
			throw new IllegalArgumentException("当前账号密码校验失败");
		}
		if ("RAW_LOGS".equals(request.purpose())) {
			if (!"VIEW RAW LOGS".equals(request.confirmationPhrase())) {
				throw new IllegalArgumentException("原始日志确认短语不正确");
			}
		}
		else {
			ExecutionDraft draft = requireDraft(request.draftId());
			requireDraftObjectScope(draft);
			String phrase = draft.billable() ? "REAL-BILLING:" + draft.id() : "EXECUTE:" + draft.id();
			if (!MessageDigest.isEqual(bytes(phrase), bytes(request.confirmationPhrase()))) {
				throw new IllegalArgumentException("执行确认短语不正确");
			}
			if (draft.billable()) {
				StpUtil.checkPermission("ocpx:api-debug:execute-billable");
			}
		}
		String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
		Grant grant = new Grant(request.draftId(), userId, sessionToken, ip, request.purpose());
		Duration ttl = "RAW_LOGS".equals(request.purpose()) ? Duration.ofMinutes(5) : Duration.ofSeconds(120);
		redisTemplate.opsForValue().set(authKey(token), json(grant), ttl);
		return new ApiDebugViews.Authorization(token, LocalDateTime.now().plus(ttl));
	}

	public ApiDebugViews.Execution execute(ApiDebugRequests.Execute request, String sessionToken, String ip) {
		ensureEnabled();
		ExecutionDraft draft = requireDraft(request.draftId());
		requireDraftObjectScope(draft);
		validateGrant(request.authorizationToken(), draft.id(), "EXECUTE", sessionToken, ip, true);
		checkRate(draft.createdBy());
		Semaphore perUser = userConcurrency.computeIfAbsent(draft.createdBy(), ignored -> new Semaphore(2));
		if (!perUser.tryAcquire()) {
			throw new IllegalArgumentException("调试并发已达上限，请稍后重试");
		}
		if (!globalConcurrency.tryAcquire()) {
			perUser.release();
			throw new IllegalArgumentException("调试并发已达上限，请稍后重试");
		}
		int transitioned = jdbcTemplate.update(
				"UPDATE scx_api_debug_execution SET status='QUEUED',update_time=NOW(3) WHERE id=? AND status='PREFLIGHT'",
				draft.id());
		if (transitioned == 0) {
			globalConcurrency.release();
			perUser.release();
			throw new IllegalArgumentException("预检草稿已执行，不能重复提交");
		}
		appendLog(draft.id(), "MANAGEMENT", "INFO", "QUEUED", "调试执行已进入受限执行器", Map.of());
		executor.submit(() -> {
			try {
				if ("INTEGRATED".equals(draft.mode())) {
					executeIntegrated(draft);
				}
				else {
					executeCustom(draft);
				}
			}
			finally {
				globalConcurrency.release();
				perUser.release();
			}
		});
		return execution(draft.id());
	}

	public ApiDebugViews.Execution executeMultipart(ApiDebugRequests.Execute request, List<MultipartFile> files,
			String sessionToken, String ip) {
		ensureEnabled();
		ExecutionDraft draft = requireDraft(request.draftId());
		if (!"CUSTOM".equals(draft.mode()) || !"FORM_DATA".equals(draft.bodyType())) {
			throw new IllegalArgumentException("文件仅允许用于自定义 FormData 调试");
		}
		validateGrant(request.authorizationToken(), draft.id(), "EXECUTE", sessionToken, ip, true);
		checkRate(draft.createdBy());
		List<UploadPart> parts = materializeFiles(files);
		Semaphore perUser = userConcurrency.computeIfAbsent(draft.createdBy(), ignored -> new Semaphore(2));
		if (!perUser.tryAcquire()) {
			throw new IllegalArgumentException("调试并发已达上限，请稍后重试");
		}
		if (!globalConcurrency.tryAcquire()) {
			perUser.release();
			throw new IllegalArgumentException("调试并发已达上限，请稍后重试");
		}
		int transitioned = jdbcTemplate.update("""
				UPDATE scx_api_debug_execution SET status='QUEUED',
				 side_effect_summary=CONCAT(side_effect_summary,? ),update_time=NOW(3) WHERE id=? AND status='PREFLIGHT'
				""", "；临时文件=" + json(parts.stream().map(UploadPart::metadata).toList()), draft.id());
		if (transitioned == 0) {
			globalConcurrency.release();
			perUser.release();
			throw new IllegalArgumentException("预检草稿已执行，不能重复提交");
		}
		appendLog(draft.id(), "MANAGEMENT", "INFO", "QUEUED", "含临时文件的 FormData 调试已进入受限执行器",
				Map.of("files", parts.size()));
		executor.submit(() -> {
			try {
				executeCustomMultipart(draft, parts);
			}
			finally {
				globalConcurrency.release();
				perUser.release();
			}
		});
		return execution(draft.id());
	}

	public ApiDebugViews.Execution execution(String id) {
		List<ApiDebugViews.Execution> rows = jdbcTemplate.query("""
				SELECT id,mode,scenario,status,monitor_id,route_version,request_method,request_url,request_headers,
				       request_body,response_status,response_headers,response_body,response_mime,response_size,
				       response_sha256,duration_ms,risk_summary,side_effect_summary,error_message,created_by,
				       create_time,update_time
				FROM scx_api_debug_execution WHERE id=? AND created_by=?
				""", this::executionRow, id, SecurityUtils.getUserId());
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("调试执行不存在");
		}
		return rows.get(0);
	}

	public Page<ApiDebugViews.Execution> executions(long current, long size) {
		long safeSize = Math.min(Math.max(size, 1), 100);
		long safeCurrent = Math.max(current, 1);
		String userId = SecurityUtils.getUserId();
		Long total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM scx_api_debug_execution WHERE status<>'PREFLIGHT' AND created_by=?", Long.class,
				userId);
		List<ApiDebugViews.Execution> rows = jdbcTemplate.query("""
				SELECT id,mode,scenario,status,monitor_id,route_version,request_method,request_url,request_headers,
				       request_body,response_status,response_headers,response_body,response_mime,response_size,
				       response_sha256,duration_ms,risk_summary,side_effect_summary,error_message,created_by,
				       create_time,update_time
				FROM scx_api_debug_execution WHERE status<>'PREFLIGHT' AND created_by=?
				ORDER BY create_time DESC LIMIT ? OFFSET ?
				""", this::executionRow, userId, safeSize, (safeCurrent - 1) * safeSize);
		Page<ApiDebugViews.Execution> page = new Page<>(safeCurrent, safeSize, total == null ? 0 : total);
		page.setRecords(rows);
		return page;
	}

	public List<ApiDebugViews.ExecutionLog> logs(String executionId, long afterSequence) {
		execution(executionId);
		return jdbcTemplate.query("""
				SELECT sequence_no,source,level,phase,message,detail_json,create_time
				FROM scx_api_debug_execution_log WHERE execution_id=? AND sequence_no>? ORDER BY sequence_no LIMIT 500
				""",
				(rs, rowNum) -> new ApiDebugViews.ExecutionLog(rs.getLong(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime()),
				executionId, afterSequence);
	}

	public ApiDebugViews.RecentLogs recentLogs(String source, String level, String logger, String keyword, long cursor,
			String authorizationToken, String sessionToken, String ip) {
		ensureEnabled();
		if (!properties.isRawLogsEnabled()) {
			throw new IllegalArgumentException("近期原始日志查询已停用");
		}
		validateGrant(authorizationToken, "RAW_LOGS", "RAW_LOGS", sessionToken, ip, false);
		String normalizedSource = "ENGINE".equalsIgnoreCase(source) ? "ENGINE" : "MANAGEMENT";
		ApiDebugViews.RecentLogs result = "ENGINE".equals(normalizedSource)
				? proxyEngineLogs(level, logger, keyword, cursor)
				: bufferedLogs("MANAGEMENT", level, logger, keyword, cursor);
		auditRawLogAccess(normalizedSource, level, logger, keyword, ip, result.records().size());
		return result;
	}

	public List<ApiDebugViews.DebugCase> cases() {
		String userId = SecurityUtils.getUserId();
		return jdbcTemplate.query(
				"""
						SELECT id,case_name,description,mode,scenario,monitor_id,request_json,created_by,updated_by,create_time,update_time
						FROM scx_api_debug_case WHERE deleted=0 AND created_by=? ORDER BY update_time DESC LIMIT 500
						""",
				(rs, rowNum) -> new ApiDebugViews.DebugCase(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getString(9), rs.getTimestamp(10).toLocalDateTime(), rs.getTimestamp(11).toLocalDateTime()),
				userId);
	}

	@Transactional
	public ApiDebugViews.DebugCase saveCase(ApiDebugRequests.CaseSave request) {
		String userId = SecurityUtils.getUserId();
		String caseId = StringUtils.hasText(request.id()) ? request.id() : id();
		if (StringUtils.hasText(request.id())) {
			Long owned = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM scx_api_debug_case WHERE id=? AND created_by=?", Long.class, caseId, userId);
			if (owned == null || owned == 0) {
				throw new IllegalArgumentException("调试用例不存在");
			}
		}
		if ("INTEGRATED".equals(request.mode())) {
			requireTarget(request.monitorId());
		}
		jdbcTemplate.update(
				"""
						INSERT INTO scx_api_debug_case
						(id,case_name,description,mode,scenario,monitor_id,request_json,created_by,updated_by,create_time,update_time,deleted)
						VALUES (?,?,?,?,?,?,?,?,?,NOW(3),NOW(3),0)
						ON DUPLICATE KEY UPDATE case_name=VALUES(case_name),description=VALUES(description),mode=VALUES(mode),
						 scenario=VALUES(scenario),monitor_id=VALUES(monitor_id),request_json=VALUES(request_json),
						 updated_by=VALUES(updated_by),update_time=NOW(3),deleted=0
						""",
				caseId, request.caseName(), request.description(), request.mode(), request.scenario(),
				request.monitorId(), request.requestJson(), userId, userId);
		return cases().stream()
			.filter(item -> item.id().equals(caseId))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("调试用例保存失败"));
	}

	public void deleteCase(String id) {
		if (jdbcTemplate.update(
				"UPDATE scx_api_debug_case SET deleted=1,updated_by=?,update_time=NOW(3)"
						+ " WHERE id=? AND created_by=? AND deleted=0",
				SecurityUtils.getUserId(), id, SecurityUtils.getUserId()) == 0) {
			throw new IllegalArgumentException("调试用例不存在");
		}
	}

	@Scheduled(cron = "0 30 3 * * ?")
	public void cleanup() {
		jdbcTemplate.update("DELETE FROM scx_api_debug_execution_log WHERE execution_id IN "
				+ "(SELECT id FROM scx_api_debug_execution WHERE expires_at<NOW(3))");
		jdbcTemplate.update("DELETE FROM scx_api_debug_execution WHERE expires_at<NOW(3)");
		jdbcTemplate.update("DELETE FROM scx_api_debug_log_access_audit WHERE expires_at<NOW(3)");
	}

	private void auditRawLogAccess(String source, String level, String logger, String keyword, String ip,
			int returnedCount) {
		jdbcTemplate.update("""
				INSERT INTO scx_api_debug_log_access_audit
				(id,user_id,source,level_filter,logger_filter,keyword_sha256,range_start,range_end,returned_count,
				 client_ip,create_time,expires_at)
				VALUES (?,?,?,?,?,?,DATE_SUB(NOW(3),INTERVAL 2 HOUR),NOW(3),?,?,NOW(3),DATE_ADD(NOW(3),INTERVAL 7 DAY))
				""", id(), SecurityUtils.getUserId(), source, truncate(level, 16), truncate(logger, 255),
				StringUtils.hasText(keyword) ? sha256(keyword) : null, returnedCount, truncate(ip, 64));
	}

	private void executeCustom(ExecutionDraft draft) {
		long started = System.nanoTime();
		try {
			jdbcTemplate.update("UPDATE scx_api_debug_execution SET status='RUNNING',update_time=NOW(3) WHERE id=?",
					draft.id());
			appendLog(draft.id(), "MANAGEMENT", "INFO", "REQUEST", "开始发送自定义 HTTPS 请求",
					Map.of("method", draft.method(), "host", Objects.toString(host(draft.url()), "")));
			ApiDebugRequests.Preflight request = new ApiDebugRequests.Preflight(draft.mode(), draft.scenario(),
					draft.monitorId(), draft.method(), draft.url(), parseMap(draft.headers()), draft.bodyType(),
					draft.body(), draft.timeoutSeconds());
			ResponseResult result = exchange(request);
			jdbcTemplate.update(
					"""
							UPDATE scx_api_debug_execution SET status='SUCCEEDED',response_status=?,response_headers=?,response_body=?,
							 response_mime=?,response_size=?,response_sha256=?,duration_ms=?,update_time=NOW(3) WHERE id=?
							""",
					result.status(), result.headers(), result.body(), result.mime(), result.size(), result.sha256(),
					elapsed(started), draft.id());
			appendLog(draft.id(), "MANAGEMENT", "INFO", "RESPONSE", "已收到目标响应",
					Map.of("status", result.status(), "size", result.size()));
		}
		catch (Exception error) {
			fail(draft.id(), started, error);
		}
	}

	private void executeCustomMultipart(ExecutionDraft draft, List<UploadPart> files) {
		long started = System.nanoTime();
		try {
			jdbcTemplate.update("UPDATE scx_api_debug_execution SET status='RUNNING',update_time=NOW(3) WHERE id=?",
					draft.id());
			validatePublicHttps(draft.url());
			MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
			parseMap(draft.body()).forEach(multipart::addFormDataPart);
			for (UploadPart file : files) {
				multipart.addFormDataPart(file.fieldName(), file.fileName(),
						RequestBody.create(file.bytes(), MediaType.get(file.contentType())));
			}
			Request.Builder request = new Request.Builder().url(draft.url());
			parseMap(draft.headers()).forEach((name, value) -> {
				if (FORBIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
					throw new IllegalArgumentException("禁止自定义请求头: " + name);
				}
				request.addHeader(name, value);
			});
			long requestStarted = System.nanoTime();
			try (Response response = httpClient.newCall(request.method(draft.method(), multipart.build()).build())
				.execute()) {
				ResponseBody responseBody = response.body();
				byte[] bytes = responseBody == null ? new byte[0]
						: responseBody.byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
				if (bytes.length > MAX_RESPONSE_BYTES) {
					throw new IllegalArgumentException("响应体超过 1 MiB 限制");
				}
				String mime = responseBody == null || responseBody.contentType() == null ? null
						: responseBody.contentType().toString();
				String text = isText(mime) ? new String(bytes, StandardCharsets.UTF_8) : null;
				jdbcTemplate.update(
						"""
								UPDATE scx_api_debug_execution SET status='SUCCEEDED',response_status=?,response_headers=?,response_body=?,
								 response_mime=?,response_size=?,response_sha256=?,duration_ms=?,update_time=NOW(3) WHERE id=?
								""",
						response.code(), json(response.headers().toMultimap()), text, mime, (long) bytes.length,
						sha256(bytes), elapsed(requestStarted), draft.id());
				appendLog(draft.id(), "MANAGEMENT", "INFO", "RESPONSE", "FormData 目标响应已接收",
						Map.of("status", response.code(), "size", bytes.length));
			}
		}
		catch (Exception error) {
			fail(draft.id(), started, error);
		}
	}

	private void executeIntegrated(ExecutionDraft draft) {
		long started = System.nanoTime();
		try {
			jdbcTemplate.update("UPDATE scx_api_debug_execution SET status='RUNNING',update_time=NOW(3) WHERE id=?",
					draft.id());
			appendLog(draft.id(), "MANAGEMENT", "INFO", "ENGINE_DISPATCH", "正在调用 OCPX 引擎内部调试入口",
					Map.of("scenario", draft.scenario()));
			ApiDebugViews.EngineExecutionRequest payload = new ApiDebugViews.EngineExecutionRequest(draft.id(),
					draft.scenario(), draft.monitorId(), draft.routeVersion(), draft.method(), draft.body(),
					parseMap(draft.headers()));
			String body = json(payload);
			String path = "/internal/api-debug/executions";
			Request.Builder builder = new Request.Builder().url(properties.getEngineBaseUrl() + path)
				.post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")));
			applyInternalGrant(builder, "POST", path, body);
			try (Response response = internalClient.newCall(builder.build()).execute()) {
				String responseBody = response.body() == null ? "" : response.body().string();
				if (!response.isSuccessful()) {
					throw new IllegalStateException("OCPX 引擎拒绝调试请求: HTTP " + response.code());
				}
				jdbcTemplate.update(
						"""
								UPDATE scx_api_debug_execution SET status='WAITING_ASYNC',response_status=?,response_headers=?,
								 response_body=?,response_mime='application/json',response_size=?,response_sha256=?,duration_ms=?,
								 update_time=NOW(3) WHERE id=?
								""",
						response.code(), json(response.headers().toMultimap()), responseBody,
						(long) responseBody.getBytes(StandardCharsets.UTF_8).length, sha256(responseBody),
						elapsed(started), draft.id());
				appendLog(draft.id(), "MANAGEMENT", "INFO", "ENGINE_ACCEPTED", "OCPX 引擎已接受完整链路执行",
						Map.of("httpStatus", response.code()));
			}
		}
		catch (Exception error) {
			fail(draft.id(), started, error);
		}
	}

	private ResponseResult exchange(ApiDebugRequests.Preflight request) throws Exception {
		validatePublicHttps(request.url());
		Request.Builder builder = new Request.Builder().url(request.url());
		int headerBytes = 0;
		for (Map.Entry<String, String> header : request.safeHeaders().entrySet()) {
			String name = header.getKey().trim();
			if (FORBIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("禁止自定义请求头: " + name);
			}
			headerBytes += name.length() + header.getValue().length();
			builder.addHeader(name, header.getValue());
		}
		if (headerBytes > 32 * 1024) {
			throw new IllegalArgumentException("请求头总大小不能超过 32 KiB");
		}
		RequestBody body = requestBody(request);
		builder.method(request.method(), permitsBody(request.method()) ? body : null);
		OkHttpClient client = httpClient.newBuilder()
			.connectTimeout(request.effectiveTimeoutSeconds(), TimeUnit.SECONDS)
			.readTimeout(request.effectiveTimeoutSeconds(), TimeUnit.SECONDS)
			.writeTimeout(request.effectiveTimeoutSeconds(), TimeUnit.SECONDS)
			.build();
		try (Response response = client.newCall(builder.build()).execute()) {
			ResponseBody responseBody = response.body();
			byte[] bytes = responseBody == null ? new byte[0]
					: responseBody.byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
			if (bytes.length > MAX_RESPONSE_BYTES) {
				throw new IllegalArgumentException("响应体超过 1 MiB 限制");
			}
			String mime = responseBody == null || responseBody.contentType() == null ? null
					: responseBody.contentType().toString();
			String text = isText(mime) ? new String(bytes, StandardCharsets.UTF_8) : null;
			return new ResponseResult(response.code(), json(response.headers().toMultimap()), text, mime,
					(long) bytes.length, sha256(bytes));
		}
	}

	private RequestBody requestBody(ApiDebugRequests.Preflight request) throws Exception {
		String body = request.body() == null ? "" : request.body();
		return switch (request.bodyType()) {
			case "JSON" -> RequestBody.create(body, MediaType.get("application/json; charset=utf-8"));
			case "TEXT" -> RequestBody.create(body, MediaType.get("text/plain; charset=utf-8"));
			case "FORM_URLENCODED" -> {
				FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
				parseMap(body).forEach(form::add);
				yield form.build();
			}
			case "FORM_DATA" -> {
				MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
				parseMap(body).forEach(multipart::addFormDataPart);
				yield multipart.build();
			}
			default -> RequestBody.create(new byte[0], null);
		};
	}

	private void applyInternalGrant(Request.Builder builder, String method, String path, String body) throws Exception {
		String nonce = UUID.randomUUID().toString().replace("-", "");
		String timestamp = Long.toString(System.currentTimeMillis());
		byte[] secret = new byte[32];
		java.security.SecureRandom.getInstanceStrong().nextBytes(secret);
		String key = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
		String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + sha256(body);
		String signature = hmac(key, canonical);
		redisTemplate.opsForValue()
			.set("scx:api-debug:internal:" + nonce,
					sha256(key) + "|" + method + "|" + path + "|" + sha256(body) + "|" + timestamp,
					Duration.ofSeconds(30));
		builder.header("X-SCX-Debug-Timestamp", timestamp)
			.header("X-SCX-Debug-Nonce", nonce)
			.header("X-SCX-Debug-Key", key)
			.header("X-SCX-Debug-Sign", signature);
	}

	private ApiDebugViews.RecentLogs proxyEngineLogs(String level, String logger, String keyword, long cursor) {
		try {
			HttpUrl url = Objects
				.requireNonNull(HttpUrl.parse(properties.getEngineBaseUrl() + "/internal/api-debug/logs"))
				.newBuilder()
				.addQueryParameter("level", level)
				.addQueryParameter("logger", logger)
				.addQueryParameter("keyword", keyword)
				.addQueryParameter("cursor", Long.toString(cursor))
				.build();
			Request.Builder builder = new Request.Builder().url(url).get();
			applyInternalGrant(builder, "GET", "/internal/api-debug/logs", "");
			try (Response response = internalClient.newCall(builder.build()).execute()) {
				if (!response.isSuccessful() || response.body() == null) {
					throw new IllegalArgumentException("无法读取 OCPX 引擎日志");
				}
				return objectMapper.readValue(response.body().string(), ApiDebugViews.RecentLogs.class);
			}
		}
		catch (Exception error) {
			throw new IllegalArgumentException("无法读取 OCPX 引擎日志: " + error.getClass().getSimpleName());
		}
	}

	private ApiDebugViews.RecentLogs bufferedLogs(String source, String level, String logger, String keyword,
			long cursor) {
		BoundedSanitizedLogAppender.QueryResult result = BoundedSanitizedLogAppender.query(level, logger, keyword,
				cursor);
		List<ApiDebugViews.RecentLog> rows = result.records()
			.stream()
			.map(row -> new ApiDebugViews.RecentLog(row.sequence(), source, row.timestamp(), row.level(), row.logger(),
					row.message()))
			.toList();
		return new ApiDebugViews.RecentLogs(rows, result.nextCursor(), result.truncated());
	}

	private void validateRequest(ApiDebugRequests.Preflight request) {
		if (request.safeHeaders().size() > 50) {
			throw new IllegalArgumentException("请求头不能超过 50 个");
		}
		int headerBytes = 0;
		for (Map.Entry<String, String> entry : request.safeHeaders().entrySet()) {
			if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
				throw new IllegalArgumentException("请求头名称和值不能为空");
			}
			headerBytes += entry.getKey().getBytes(StandardCharsets.UTF_8).length
					+ entry.getValue().getBytes(StandardCharsets.UTF_8).length;
		}
		if (headerBytes > 32 * 1024) {
			throw new IllegalArgumentException("请求头总大小不能超过 32 KiB");
		}
		if (request.body() != null && request.body().getBytes(StandardCharsets.UTF_8).length > 512 * 1024) {
			throw new IllegalArgumentException("请求体不能超过 512 KiB");
		}
		if ("INTEGRATED".equals(request.mode())
				&& (!StringUtils.hasText(request.monitorId()) || !StringUtils.hasText(request.scenario()))) {
			throw new IllegalArgumentException("已接入模式必须选择已发布路由和方向");
		}
		if ("JSON".equals(request.bodyType()) && StringUtils.hasText(request.body())) {
			try {
				objectMapper.readTree(request.body());
			}
			catch (Exception error) {
				throw new IllegalArgumentException("JSON 请求体格式不正确");
			}
		}
	}

	static void validatePublicHttps(String url) {
		HttpUrl parsed = HttpUrl.parse(url);
		if (parsed == null || !"https".equalsIgnoreCase(parsed.scheme())) {
			throw new IllegalArgumentException("自定义请求仅允许 HTTPS");
		}
		if (parsed.port() != 443 && parsed.port() != 8443) {
			throw new IllegalArgumentException("自定义请求仅允许 443 或 8443 端口");
		}
		if (StringUtils.hasText(parsed.username()) || StringUtils.hasText(parsed.password())) {
			throw new IllegalArgumentException("自定义请求 URL 不允许包含用户凭据");
		}
		validateAddresses(parsed.host());
	}

	static List<InetAddress> validateAddresses(String hostname) {
		try {
			List<InetAddress> addresses = List.of(InetAddress.getAllByName(hostname));
			if (addresses.isEmpty()) {
				throw new IllegalArgumentException("目标域名无法解析");
			}
			for (InetAddress address : addresses) {
				if (!isPublic(address)) {
					throw new IllegalArgumentException("目标域名解析到非公网地址");
				}
			}
			return addresses;
		}
		catch (IllegalArgumentException error) {
			throw error;
		}
		catch (Exception error) {
			throw new IllegalArgumentException("目标域名无法解析");
		}
	}

	static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			int first = bytes[0] & 0xff;
			int second = bytes[1] & 0xff;
			return !(first == 0 || first == 10 || first == 127 || first == 169 && second == 254
					|| first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168
					|| first == 100 && second >= 64 && second <= 127 || first >= 224);
		}
		if (address instanceof Inet6Address) {
			int first = bytes[0] & 0xff;
			return (first & 0xfe) != 0xfc;
		}
		return false;
	}

	private ApiDebugViews.Target requireTarget(String monitorId) {
		return requireTarget(monitorId, OcpxAccessScope.PermissionLevel.VIEW);
	}

	private ApiDebugViews.Target requireTarget(String monitorId, OcpxAccessScope.PermissionLevel level) {
		ApiDebugViews.Target target = targets(monitorId).stream()
			.filter(item -> item.monitorId().equals(monitorId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("已发布调试目标不存在"));
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, target.advertiserId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, target.channelId(), level);
		return target;
	}

	private void requireDraftObjectScope(ExecutionDraft draft) {
		if ("INTEGRATED".equals(draft.mode())) {
			requireTarget(draft.monitorId(), OcpxAccessScope.PermissionLevel.OPERATE);
		}
	}

	private TargetScope targetScope(OcpxAccessScope.PermissionLevel level) {
		OcpxAccessScope.Snapshot snapshot = accessScope.current();
		StringBuilder clause = new StringBuilder();
		List<Object> args = new ArrayList<>();
		appendTargetRestriction(clause, args, "m.advertiser_id",
				snapshot.restriction(OcpxAccessScope.ScopeType.ADVERTISER, level));
		appendTargetRestriction(clause, args, "m.channel_id",
				snapshot.restriction(OcpxAccessScope.ScopeType.CHANNEL, level));
		return new TargetScope(clause.toString(), List.copyOf(args));
	}

	private void appendTargetRestriction(StringBuilder clause, List<Object> args, String column,
			OcpxAccessScope.Restriction restriction) {
		if (restriction.unrestricted()) {
			return;
		}
		if (restriction.ids().isEmpty()) {
			clause.append(" AND 1=0");
			return;
		}
		clause.append(" AND ")
			.append(column)
			.append(" IN (")
			.append(String.join(",", java.util.Collections.nCopies(restriction.ids().size(), "?")))
			.append(")");
		args.addAll(restriction.ids());
	}

	private ExecutionDraft requireDraft(String id) {
		List<ExecutionDraft> rows = jdbcTemplate.query(
				"""
						SELECT e.id,e.mode,e.scenario,e.monitor_id,e.request_method,e.request_url,e.request_headers,e.request_body,
						       e.created_by,e.route_version,e.body_type,e.timeout_seconds,m.billing_mode
						FROM scx_api_debug_execution e LEFT JOIN scx_ocpx_monitor_link m ON m.id=e.monitor_id
						WHERE e.id=? AND e.status='PREFLIGHT' AND e.create_time>DATE_SUB(NOW(3),INTERVAL 10 MINUTE)
						""",
				(rs, rowNum) -> new ExecutionDraft(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
						rs.getObject(10, Long.class), rs.getString(11), rs.getInt(12),
						isBillable(rs.getString(13), rs.getString(3), rs.getString(8))),
				id);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("预检草稿不存在或已过期");
		}
		ExecutionDraft draft = rows.get(0);
		if (!SecurityUtils.getUserId().equals(draft.createdBy())) {
			throw new IllegalArgumentException("预检草稿不存在或已过期");
		}
		return draft;
	}

	private void validateGrant(String token, String draftId, String purpose, String sessionToken, String ip,
			boolean consume) {
		String key = authKey(token);
		String raw = consume ? redisTemplate.opsForValue().getAndDelete(key) : redisTemplate.opsForValue().get(key);
		if (!StringUtils.hasText(raw)) {
			throw new IllegalArgumentException("二次认证已过期或已使用");
		}
		try {
			Grant grant = objectMapper.readValue(raw, Grant.class);
			if (!Objects.equals(grant.draftId(), draftId) || !Objects.equals(grant.userId(), SecurityUtils.getUserId())
					|| !Objects.equals(grant.sessionToken(), sessionToken) || !Objects.equals(grant.ip(), ip)
					|| !Objects.equals(grant.purpose(), purpose)) {
				throw new IllegalArgumentException("二次认证与当前会话不匹配");
			}
		}
		catch (IllegalArgumentException error) {
			throw error;
		}
		catch (Exception error) {
			throw new IllegalArgumentException("二次认证数据无效");
		}
	}

	private void checkRate(String userId) {
		long now = System.currentTimeMillis();
		Deque<Long> window = rateWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
		synchronized (window) {
			while (!window.isEmpty() && window.peekFirst() < now - 60_000) {
				window.removeFirst();
			}
			if (window.size() >= 30) {
				throw new IllegalArgumentException("每分钟最多执行 30 次调试请求");
			}
			window.addLast(now);
		}
	}

	private List<UploadPart> materializeFiles(List<MultipartFile> files) {
		List<UploadPart> parts = new ArrayList<>();
		long total = 0;
		for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
			if (file.getSize() > 5L * 1024 * 1024) {
				throw new IllegalArgumentException("单个文件不能超过 5 MiB");
			}
			total += file.getSize();
			if (total > 10L * 1024 * 1024) {
				throw new IllegalArgumentException("文件总大小不能超过 10 MiB");
			}
			try {
				byte[] bytes = file.getBytes();
				String name = Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin")
					.replaceAll("[\\r\\n]", "_");
				String type = StringUtils.hasText(file.getContentType()) ? file.getContentType()
						: "application/octet-stream";
				parts.add(new UploadPart(file.getName(), name, type, bytes,
						Map.of("fileName", name, "contentType", type, "size", bytes.length, "sha256", sha256(bytes))));
			}
			catch (Exception error) {
				throw new IllegalArgumentException("临时文件读取失败");
			}
		}
		return List.copyOf(parts);
	}

	private void appendLog(String executionId, String source, String level, String phase, String message,
			Map<String, ?> detail) {
		for (int attempt = 0; attempt < 4; attempt++) {
			Long sequence = jdbcTemplate.queryForObject(
					"SELECT COALESCE(MAX(sequence_no),0)+1 FROM scx_api_debug_execution_log WHERE execution_id=?",
					Long.class, executionId);
			try {
				jdbcTemplate.update("""
						INSERT INTO scx_api_debug_execution_log
						(id,execution_id,sequence_no,source,level,phase,message,detail_json,create_time)
						VALUES (?,?,?,?,?,?,?,?,NOW(3))
						""", id(), executionId, sequence == null ? 1 : sequence, source, level, phase,
						sanitize(message), sanitize(json(detail)));
				break;
			}
			catch (DuplicateKeyException error) {
				if (attempt == 3) {
					throw error;
				}
			}
		}
		try (MDC.MDCCloseable ignored = MDC.putCloseable("debugExecutionId", executionId)) {
			String safeMessage = truncate(sanitize(message), 2000);
			if ("ERROR".equalsIgnoreCase(level)) {
				LOGGER.error("API debug source={} phase={} message={}", source, phase, safeMessage);
			}
			else if ("WARN".equalsIgnoreCase(level)) {
				LOGGER.warn("API debug source={} phase={} message={}", source, phase, safeMessage);
			}
			else {
				LOGGER.info("API debug source={} phase={} message={}", source, phase, safeMessage);
			}
		}
	}

	private void fail(String id, long started, Exception error) {
		String message = sanitize(
				error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "执行失败"));
		jdbcTemplate.update(
				"UPDATE scx_api_debug_execution SET status='FAILED',error_message=?,duration_ms=?,update_time=NOW(3) WHERE id=?",
				truncate(message, 1000), elapsed(started), id);
		appendLog(id, "MANAGEMENT", "ERROR", "FAILED", "调试执行失败", Map.of("error", message));
	}

	private ApiDebugViews.Execution executionRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new ApiDebugViews.Execution(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getObject(6, Long.class), rs.getString(7), rs.getString(8), rs.getString(9),
				rs.getString(10), rs.getObject(11, Integer.class), rs.getString(12), rs.getString(13), rs.getString(14),
				rs.getObject(15, Long.class), rs.getString(16), rs.getObject(17, Long.class), rs.getString(18),
				rs.getString(19), rs.getString(20), rs.getString(21), rs.getTimestamp(22).toLocalDateTime(),
				rs.getTimestamp(23).toLocalDateTime());
	}

	private Map<String, String> parseMap(String json) {
		if (!StringUtils.hasText(json)) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
			});
		}
		catch (Exception error) {
			throw new IllegalArgumentException("表单或请求头必须是字符串键值对象");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (Exception error) {
			throw new IllegalArgumentException("调试数据无法序列化");
		}
	}

	private static boolean isClick(String body) {
		return body != null && Pattern.compile("(?i)\\\"event\\\"\\s*:\\s*\\\"click\\\"").matcher(body).find();
	}

	static boolean isBillable(String billingMode, String scenario, String body) {
		return "CPC".equals(billingMode) && "DOWNSTREAM_TO_UPSTREAM".equals(scenario)
				&& (!StringUtils.hasText(body) || isClick(body));
	}

	private static boolean permitsBody(String method) {
		return Set.of("POST", "PUT", "PATCH", "DELETE").contains(method);
	}

	private static boolean isText(String mime) {
		if (mime == null) {
			return false;
		}
		String value = mime.toLowerCase(Locale.ROOT);
		return value.startsWith("text/") || value.contains("json") || value.contains("xml")
				|| value.contains("javascript") || value.contains("x-www-form-urlencoded");
	}

	private static String host(String url) {
		if (!StringUtils.hasText(url)) {
			return null;
		}
		try {
			URI uri = URI.create(url);
			return uri.getHost() == null ? null : uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
		}
		catch (Exception ignored) {
			return null;
		}
	}

	private static String sanitize(String value) {
		if (value == null) {
			return null;
		}
		return truncate(BoundedSanitizedLogAppender.sanitizeText(value), 4000);
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	private static long elapsed(long started) {
		return Math.max(0, (System.nanoTime() - started) / 1_000_000);
	}

	private static String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static String authKey(String token) {
		return "scx:api-debug:authorization:" + sha256(token);
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String sha256(String value) {
		return sha256(bytes(value == null ? "" : value));
	}

	private static String sha256(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		}
		catch (Exception error) {
			throw new IllegalStateException("SHA-256 unavailable", error);
		}
	}

	private static String hmac(String key, String value) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
	}

	private void ensureEnabled() {
		if (!properties.isEnabled()) {
			throw new IllegalArgumentException("接口调试功能已停用");
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}

	private record ResponseResult(int status, String headers, String body, String mime, long size, String sha256) {
	}

	private record Grant(String draftId, String userId, String sessionToken, String ip, String purpose) {
	}

	private record ExecutionDraft(String id, String mode, String scenario, String monitorId, String method, String url,
			String headers, String body, String createdBy, Long routeVersion, String bodyType, int timeoutSeconds,
			boolean billable) {
	}

	private record TargetScope(String clause, List<Object> args) {
	}

	private record UploadPart(String fieldName, String fileName, String contentType, byte[] bytes,
			Map<String, Object> metadata) {
	}

}
