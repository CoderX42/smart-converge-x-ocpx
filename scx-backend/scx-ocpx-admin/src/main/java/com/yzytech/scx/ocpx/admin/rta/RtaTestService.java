package com.yzytech.scx.ocpx.admin.rta;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.mapper.RtaManagementSqlMapper;
import com.yzytech.scx.ocpx.admin.service.AdvertiserScope;
import com.yzytech.scx.ocpx.rta.contract.OppoMediaConfig;
import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import com.yzytech.scx.ocpx.rta.contract.RtaProtocols;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * Exercises the real OPPO wire endpoint; never creates clicks, callbacks or billing
 * facts.
 */
@Service
@RequiredArgsConstructor
public class RtaTestService {

	private final RtaManagementSqlMapper mapper;

	private final AdvertiserScope scope;

	private final RtaTestProperties properties;

	private final OcpxTrackingProperties tracking;

	private final StringRedisTemplate redis;

	private final RtaTestHttpClient http;

	public record Input(String requestId, String oaid) {
	}

	public record Preview(String taskId, String monitorName, String mediaName, String upstreamName, String mediaProtocol,
			String upstreamProtocol, Integer strategyId, String endpoint, Map<String, String> plan, String requestId,
			List<String> blockers) {
	}

	public Preview preview(String taskId) {
		scope.requireSystemAdministrator();
		Map<String, Object> route = route(taskId);
		List<String> blockers = blockers(route);
		String endpoint = "";
		try {
			endpoint = endpoint(route).toString();
		}
		catch (BootBusinessException e) {
			blockers.add(e.getMessage());
		}
		Map<String, String> plan = new LinkedHashMap<>();
		JSONObject config = JSON.parseObject(text(route, "upstream_config", "{}"));
		if (config != null)
			for (String field : List.of("aid", "did"))
				if (config.get(field) instanceof String value)
					plan.put(field, value);
		return new Preview(taskId, text(route, "task_name", ""), text(route, "media_name", ""),
				text(route, "upstream_name", ""), text(route, "media_protocol", ""),
				text(route, "upstream_protocol", ""),
				route.get("strategy_id") instanceof Number n ? n.intValue() : null, endpoint, plan,
				"SCX-RTA-" + UUID.randomUUID().toString().replace("-", ""), List.copyOf(blockers));
	}

	public Map<String, Object> execute(String taskId, Input input) {
		scope.requireSystemAdministrator();
		validateId(input == null ? null : input.requestId());
		if (input.oaid() == null || input.oaid().isBlank() || input.oaid().length() > 256 || input.oaid().contains("__")
				|| input.oaid().chars().anyMatch(Character::isISOControl))
			throw new BootBusinessException("请填写有效的 OAID");
		Map<String, Object> route = route(taskId);
		List<String> blockers = blockers(route);
		if (!blockers.isEmpty())
			throw new BootBusinessException(String.join("；", blockers));
		URI endpoint = endpoint(route);
		String key = key(input.requestId());
		String fingerprint = hash(taskId + ":" + input.oaid());
		Map<String, Object> record = new LinkedHashMap<>();
		record.put("taskId", taskId);
		record.put("requestId", input.requestId());
		record.put("fingerprint", fingerprint);
		record.put("mediaId", text(route, "media_id", ""));
		record.put("strategyId", ((Number) route.get("strategy_id")).intValue());
		record.put("submission", "STARTED");
		record.put("startedAt", System.currentTimeMillis());
		if (!Boolean.TRUE
			.equals(redis.opsForValue().setIfAbsent(key, JSON.toJSONString(record), Duration.ofHours(24)))) {
			JSONObject old = load(taskId, input.requestId());
			if (!fingerprint.equals(old.getString("fingerprint")))
				throw new BootBusinessException("同一测试 ID 的任务和 OAID 不能修改，请创建新测试");
			return result(taskId, input.requestId());
		}
		// Persist before sending. Unknown transport outcomes must never cause automatic
		// resubmission.
		Map<String, Object> body = signedBody(route, input, System.currentTimeMillis());
		var response = http.post(endpoint, JSON.toJSONString(body));
		record.put("httpStatus", response.status());
		record.put("durationMs", response.durationMs());
		record.put("submission", "UNKNOWN");
		record.put("message", "未取得有效同步响应，请查询决策状态；此测试 ID 不会自动重发");
		if (response.error() == null) {
			try {
				JSONObject wire = JSON.parseObject(response.body());
				JSONObject data = wire.getJSONObject("data");
				Object ret = wire.get("ret");
				if (response.status() == 200 && ret instanceof Integer && data != null
						&& input.requestId().equals(data.getString("id"))
						&& data.get("agree_type_list") instanceof List<?> agreed
						&& agreed.stream().allMatch(v -> v instanceof Integer && v.equals(record.get("strategyId")))
						&& agreed.size() <= 1 && (((Integer) ret) == 0 || (((Integer) ret) == 1 && agreed.isEmpty()))) {
					record.put("submission", "RECEIVED");
					record.put("ret", ret);
					record.put("agreed", !agreed.isEmpty());
					record.put("message",
							((Integer) ret) != 0 ? "RTA 请求未正常完成" : agreed.isEmpty() ? "本次策略未获允许" : "本次策略已获允许");
				}
				else {
					record.put("message", "Engine 返回拒绝或不符合协议的响应，请检查配置与流程日志");
				}
			}
			catch (RuntimeException ignored) {
			}
		}
		// This record contains only safe metadata, never the OAID, signature or partner
		// body.
		redis.opsForValue().set(key, JSON.toJSONString(record), Duration.ofHours(24));
		return result(taskId, input.requestId());
	}

	public Map<String, Object> result(String taskId, String requestId) {
		scope.requireSystemAdministrator();
		validateId(requestId);
		JSONObject record = load(taskId, requestId);
		Map<String, Object> result = new LinkedHashMap<>();
		for (String field : List.of("taskId", "requestId", "strategyId", "submission", "startedAt", "httpStatus",
				"durationMs", "ret", "agreed", "message"))
			if (record.containsKey(field))
				result.put(field, record.get(field));
		String internalId = "oppo." + hash(requestId + ":" + record.getInteger("strategyId"));
		String relayId = redis.opsForValue()
			.get(RtaContract.PREFIX + "idem:" + record.getString("mediaId") + ":" + internalId);
		if (relayId != null) {
			String raw = redis.opsForValue().get(RtaContract.PREFIX + "state:" + relayId);
			if (raw != null) {
				JSONObject state = JSON.parseObject(raw);
				if (!record.getString("mediaId").equals(state.getString("mediaId"))
						|| !internalId.equals(state.getString("requestId"))
						|| state.getJSONArray("bindings")
							.stream()
							.noneMatch(b -> taskId.equals(((JSONObject) b).getString("taskId"))))
					throw new BootBusinessException("测试状态关联异常");
				for (String field : List.of("relayRequestId", "providerRequestId", "status", "deliveryStatus",
						"reasonCode", "receivedAt", "completedAt", "deadline", "expiresAt", "upstreamAttempts"))
					if (state.containsKey(field))
						result.put(field, state.get(field));
			}
		}
		return result;
	}

	private JSONObject load(String taskId, String id) {
		String raw = redis.opsForValue().get(key(id));
		if (raw == null)
			throw new BootBusinessException("测试不存在或已超过24小时保留期");
		JSONObject record = JSON.parseObject(raw);
		if (!taskId.equals(record.getString("taskId")))
			throw new BootBusinessException("测试不存在或无权访问");
		return record;
	}

	private Map<String, Object> route(String id) {
		if (id == null || !id.matches("[A-Za-z0-9_-]{1,32}"))
			throw new BootBusinessException("任务不存在或无权访问");
		Map<String, Object> route = mapper.selectRtaTestRoute(id);
		if (route == null)
			throw new BootBusinessException("任务不存在或无权访问");
		return route;
	}

	private List<String> blockers(Map<String, Object> route) {
		List<String> issues = new ArrayList<>();
		for (String flag : List.of("rta_enabled", "binding_enabled", "media_enabled", "upstream_enabled"))
			if (!Boolean.TRUE.equals(route.get(flag)) && !(route.get(flag) instanceof Number n && n.intValue() == 1))
				issues.add("任务、绑定、媒体与上游 RTA 均须启用");
		if (!"OPPO".equals(route.get("media_protocol")) || !"WEIFENG_RTA".equals(route.get("upstream_protocol")))
			issues.add("此入口首版支持 OPPO 媒体接入微风 RTA");
		String secret = properties.getCredentials().get(text(route, "credential_ref", ""));
		if (secret == null || secret.length() < 32)
			issues.add("Boot 未配置同名媒体 RTA 测试签名密钥（须与 Engine 一致）");
		if (!(route.get("strategy_id") instanceof Number))
			issues.add("请先保存监测任务 RTA 绑定以生成策略 ID");
		try {
			OppoMediaConfig.validate(config(route, "media_config"));
			if ("WEIFENG_RTA".equals(route.get("upstream_protocol")))
				RtaProtocols.require("WEIFENG_RTA")
					.validateRoute(text(route, "link_url", ""), config(route, "upstream_config"));
		}
		catch (RuntimeException e) {
			issues.add("RTA 协议配置或微风点击计划映射无效");
		}
		return issues.stream().distinct().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	Map<String, Object> signedBody(Map<String, Object> route, Input input, long now) {
		Map<String, String> config = OppoMediaConfig.validate(config(route, "media_config"));
		String owner = "VALUE".equals(config.get("ownerMode")) ? config.get("ownerId") : "";
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("request_id", input.requestId());
		body.put("oaid", input.oaid());
		body.put("request_time", now);
		body.put("channel_name", "OPPO");
		body.put("ad_type_list", List.of(((Number) route.get("strategy_id")).intValue()));
		if (!owner.isEmpty())
			body.put("owner_id", Long.parseLong(owner));
		body.put("sign", digest("MD5", input.requestId() + owner + "OPPO" + now
				+ properties.getCredentials().get(route.get("credential_ref"))));
		return body;
	}

	private URI endpoint(Map<String, Object> route) {
		try {
			URI base = URI.create(tracking.getEngineBaseUrl());
			String ref = text(route, "credential_ref", "");
			if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
					|| base.getFragment() != null
					|| !("https".equals(base.getScheme()) || "http".equals(base.getScheme())
							&& Set.of("127.0.0.1", "localhost", "[::1]").contains(base.getHost()))
					|| !ref.matches("[A-Za-z0-9_-]{1,64}"))
				throw new IllegalArgumentException();
			return URI.create(base.toString().replaceAll("/$", "") + "/rta/oppo/" + ref);
		}
		catch (RuntimeException e) {
			throw new BootBusinessException("Engine 测试地址或媒体凭据引用未正确配置");
		}
	}

	private static Map<String, String> config(Map<String, Object> row, String key) {
		Map<String, String> result = new LinkedHashMap<>();
		JSONObject source = JSON.parseObject(text(row, key, "{}"));
		if (source != null)
			source.forEach((k, v) -> {
				if (!(v instanceof String))
					throw new IllegalArgumentException();
				result.put(k, (String) v);
			});
		return result;
	}

	private static String text(Map<String, Object> row, String key, String fallback) {
		return Objects.toString(row.get(key), fallback);
	}

	private static void validateId(String id) {
		if (id == null || !id.matches("SCX-RTA-[a-f0-9]{32}"))
			throw new BootBusinessException("测试 ID 无效，请生成新测试");
	}

	private static String key(String id) {
		return RtaContract.PREFIX + "admin-test:" + id;
	}

	private static String hash(String value) {
		return digest("SHA-256", value);
	}

	private static String digest(String algorithm, String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
