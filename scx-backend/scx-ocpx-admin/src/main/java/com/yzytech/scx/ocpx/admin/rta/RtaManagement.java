package com.yzytech.scx.ocpx.admin.rta;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.service.AdvertiserScope;
import com.yzytech.scx.ocpx.rta.contract.RtaContract;
import com.yzytech.scx.ocpx.rta.contract.RtaProtocols;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.yzytech.scx.ocpx.admin.mapper.RtaManagementSqlMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

@Service
public class RtaManagement {

	private final RtaManagementSqlMapper sqlMapper;

	private final AdvertiserScope scope;

	private final StringRedisTemplate redis;

	@Value("${app.rta-relay.public-base-url:}")
	private String publicBaseUrl;

	@Value("${app.rta-relay.allowed-hosts:}")
	private List<String> allowedHosts;

	public RtaManagement(RtaManagementSqlMapper sqlMapper, AdvertiserScope scope, StringRedisTemplate redis) {
		this.sqlMapper = sqlMapper;
		this.scope = scope;
		this.redis = redis;
	}

	public record Page(List<Map<String, Object>> records, long total, long current, long size, String dataAsOf) {
	}

	public Page list(String kind, Map<String, String> query) {
		String advertiser = scope.constrain(query.get("advertiserId"));
		long current = number(query.get("current"), 1, 1, 1000), size = number(query.get("size"), 30, 1, 100);
		if (!Set.of("upstreams", "media", "bindings", "logs", "reports").contains(kind))
			throw new BootBusinessException("未知 RTA 页面");
		long now = System.currentTimeMillis(), start = now - 86400000L, end = now;
		if (kind.equals("logs") || kind.equals("reports")) {
			start = number(query.get("startTime"), start, 0, Long.MAX_VALUE);
			end = number(query.get("endTime"), end, 0, Long.MAX_VALUE);
			if (start > end || end - start > 31L * 86400000)
				throw new BootBusinessException("时间范围最多31天");
		}
		var filters = new HashMap<>(query);
		if (filters.get("keyword") != null)
			filters.put("keyword", filters.get("keyword").trim());
		var criteria = new RtaManagementSqlMapper.Query(kind, advertiser, filters, new java.sql.Timestamp(start),
				new java.sql.Timestamp(end), new java.sql.Timestamp(now - 86400000L), size, (current - 1) * size);
		Long total = sqlMapper.count(criteria);
		String asOf = kind.equals("logs") || kind.equals("reports") ? sqlMapper.dataAsOf(criteria) : null;
		List<Map<String, Object>> rows = sqlMapper.selectPage(criteria).stream().map(this::camel).toList();
		for (var row : rows) {
			if (kind.equals("upstreams")) {
				var protocol = RtaProtocols.require(row.get("protocol").toString());
				row.put("protocolConfig", parseConfig(row.get("protocolConfig")));
				row.put("decisionMode", protocol.mode());
				if (protocol.mode().equals("ASYNC_CALLBACK"))
					row.put("callbackUrl", publicBaseUrl + "/rta/callbacks/" + row.get("accessCode"));
			}
			if (kind.equals("media")) {
				row.put("protocolConfig", parseConfig(row.get("protocolConfig")));
				row.put("requestUrl", publicBaseUrl + ("OPPO".equals(row.get("protocol"))
						? "/rta/oppo/" + row.get("credentialRef") : "/rta/requests"));
			}
			if (kind.equals("bindings") && "OPPO".equals(row.get("mediaProtocol")) && row.get("strategyId") != null) {
				row.put("requestUrl", publicBaseUrl + "/rta/oppo/" + row.get("mediaAccessCode"));
				row.put("clickUrl", publicBaseUrl + "/oppo/click/" + row.get("mediaAccessCode") + "/"
						+ row.get("strategyId")
						+ "?request_id=__REQ__&oaid=__OAID__&ad_id=__ADID__&ts=__TS__&model=__MODEL__&ip=__IP__&ua=__UA__&osv=__OSV__");
			}
			if (kind.equals("reports")) {
				long count = ((Number) row.get("requestCount")).longValue();
				long done = 0;
				for (String f : List.of("allowCount", "denyCount", "errorCount", "timeoutCount"))
					done += ((Number) row.get(f)).longValue();
				row.put("pendingCount", Math.max(0, count - done));
				row.put("allowRate", count == 0 ? 0 : ((Number) row.get("allowCount")).doubleValue() / count);
			}
		}
		return new Page(rows, total == null ? 0 : total, current, size, asOf);
	}

	public List<Map<String, Object>> options(String kind, Map<String, String> query) {
		String advertiser = scope.constrain(query.get("advertiserId"));
		if (!Set.of("advertisers", "media", "tasks", "upstreams").contains(kind))
			throw new BootBusinessException("未知选项类型");
		return sqlMapper
			.selectOptions(new RtaManagementSqlMapper.Query(kind, advertiser, query, null, null, null, 100, 0));
	}

	@Transactional
	public void save(String kind, Map<String, Object> body) {
		var allowed = Map.of("upstreams",
				Set.of("id", "advertiserId", "name", "protocol", "protocolConfig", "accessCode", "endpoint",
						"credentialRef", "timeoutMs", "admissionSeconds", "enabled", "version"),
				"media",
				Set.of("mediaId", "notifyUrl", "credentialRef", "enabled", "version", "protocol", "protocolConfig"),
				"bindings",
				Set.of("monitorTaskId", "upstreamId", "productId", "externalTaskId", "enabled", "version", "oppoPkg"));
		if (!allowed.containsKey(kind) || !allowed.get(kind).containsAll(body.keySet()))
			throw new BootBusinessException("未知 RTA 配置字段");
		if (body.containsKey("enabled") && !(body.get("enabled") instanceof Boolean))
			throw new BootBusinessException("状态字段无效");
		String id = text(body, "id", 32, false);
		boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
		long version = number(Objects.toString(body.get("version"), null), 0, 0, Long.MAX_VALUE);
		if (kind.equals("upstreams")) {
			scope.requireSystemAdministrator();
			String advertiser = text(body, "advertiserId", 32, true), name = text(body, "name", 128, true),
					code = text(body, "accessCode", 64, false), endpoint = text(body, "endpoint", 2048, false),
					ref = text(body, "credentialRef", 64, false);
			String protocolCode = Objects.toString(body.get("protocol"), "GENERIC_ASYNC");
			RtaProtocols.Definition protocol;
			Map<String, String> config;
			try {
				protocol = RtaProtocols.require(protocolCode);
				config = protocol.validateConfig(parseConfig(body.get("protocolConfig")));
			}
			catch (IllegalArgumentException e) {
				throw new BootBusinessException(e.getMessage());
			}
			if (code.isEmpty()) {
				if (!id.isEmpty())
					throw new BootBusinessException("接入编号不可修改");
				code = UUID.randomUUID().toString().replace("-", "");
			}
			if (!protocol.fixedEndpoint().isEmpty()) {
				if (!endpoint.isEmpty() && !endpoint.equals(protocol.fixedEndpoint()))
					throw new BootBusinessException("协议地址不可修改");
				endpoint = protocol.fixedEndpoint();
			}
			if (!id.isEmpty()) {
				var old = sqlMapper.findUpstream(id);
				if (old == null || !advertiser.equals(old.get("advertiser_id"))
						|| !protocolCode.equals(old.get("protocol")))
					throw new BootBusinessException("接入不可用或协议不可修改");
			}
			identifier(code);
			if (protocol.credentialRequired())
				identifier(ref);
			else if (!ref.isEmpty())
				throw new BootBusinessException("当前协议不使用认证凭据");
			exists(sqlMapper.countAdvertiser(advertiser));
			url(endpoint);
			int timeout = (int) number(Objects.toString(body.get("timeoutMs"), null), 5000, 100, 30000),
					ttl = (int) number(Objects.toString(body.get("admissionSeconds"), null), 300, 1, 86400);
			if (id.isEmpty())
				sqlMapper.insertSaveRtaUpstream(new RtaManagementSqlMapper.InsertSaveRtaUpstreamCommand(
						UUID.randomUUID().toString().replace("-", ""), advertiser, name, code, endpoint, ref, timeout,
						ttl, enabled, protocolCode, JSON.toJSONString(config)));
			else
				changed(sqlMapper.updateSaveRtaUpstream(
						new RtaManagementSqlMapper.UpdateSaveRtaUpstreamCommand(name, endpoint, ref, timeout, ttl,
								enabled, id, advertiser, code, version, protocolCode, JSON.toJSONString(config))));
		}
		else if (kind.equals("media")) {
			scope.requireSystemAdministrator();
			String protocol = Objects.toString(body.get("protocol"), "GENERIC_ASYNC");
			if (!Set.of("GENERIC_ASYNC", "OPPO").contains(protocol))
				throw new BootBusinessException("媒体协议无效");
			Map<String, String> config = parseConfig(body.get("protocolConfig"));
			if (protocol.equals("OPPO")) {
				try {
					config = com.yzytech.scx.ocpx.rta.contract.OppoMediaConfig.validate(config);
				}
				catch (IllegalArgumentException e) {
					throw new BootBusinessException(e.getMessage());
				}
			}
			else if (!config.isEmpty())
				throw new BootBusinessException("通用媒体不支持协议参数");
			String media = text(body, "mediaId", 32, true),
					notify = text(body, "notifyUrl", 2048, protocol.equals("GENERIC_ASYNC")),
					ref = text(body, "credentialRef", 64, true);
			exists(sqlMapper.countMedia(media));
			identifier(ref);
			if (protocol.equals("GENERIC_ASYNC"))
				url(notify);
			else
				notify = "";
			if (version == 0)
				sqlMapper.insertSaveRtaMediaAccess(media, notify, ref, enabled);
			else
				changed(sqlMapper.updateSaveRtaMediaAccess(notify, ref, enabled, media, version));
			sqlMapper.updateMediaProtocol(media, protocol, JSON.toJSONString(config));
		}
		else if (kind.equals("bindings")) {
			String task = text(body, "monitorTaskId", 32, true), upstream = text(body, "upstreamId", 32, true),
					product = text(body, "productId", 128, false), external = text(body, "externalTaskId", 128, false);
			List<Map<String, Object>> tasks = sqlMapper.lockTask(task);
			if (tasks.size() != 1)
				throw new BootBusinessException("任务不可用");
			var t = tasks.get(0);
			scope.requireOwner(t.get("advertiser_id").toString());
			var mediaAccess = sqlMapper.selectSaveRtaMediaAccess(t.get("media_id"));
			var access = sqlMapper.findUpstream(upstream);
			if (access == null || !Objects.equals(access.get("advertiser_id"), t.get("advertiser_id")))
				throw new BootBusinessException("广告主接入不可用");
			var protocol = RtaProtocols.require(access.get("protocol").toString());
			if (mediaAccess != null && mediaAccess.stream().anyMatch(m -> "OPPO".equals(m.get("protocol")))
					&& !"WEIFENG_RTA".equals(protocol.code()))
				throw new BootBusinessException("OPPO 媒体只能绑定微风 RTA 接入");
			if (protocol.bindingFields().isEmpty()) {
				product = null;
				external = null;
				try {
					protocol.validateRoute(sqlMapper.selectLink(t.get("promotion_link_id")),
							parseConfig(access.get("protocol_config")));
				}
				catch (IllegalArgumentException e) {
					throw new BootBusinessException(e.getMessage());
				}
			}
			else if (product.isBlank() || external.isBlank())
				throw new BootBusinessException("请填写上游产品和任务标识");
			Long valid = sqlMapper.countValidRoute(t.get("media_id"), upstream, t.get("advertiser_id"), enabled);
			if (valid == null || valid != 1)
				throw new BootBusinessException("广告主接入或媒体配置不可用");
			Long duplicate = protocol.bindingFields().isEmpty() ? 0L
					: sqlMapper.selectSaveRtaBinding(t.get("media_id"), upstream, product, external, task);
			if (enabled && duplicate != null && duplicate > 0)
				throw new BootBusinessException("同一媒体的产品/任务映射存在歧义");
			if (version == 0)
				sqlMapper.insertSaveRtaBinding(task, upstream, product, external, enabled);
			else
				changed(sqlMapper.updateSaveRtaBinding(new RtaManagementSqlMapper.UpdateSaveRtaBindingCommand(upstream,
						product, external, enabled, task, version)));
			String pkg = text(body, "oppoPkg", 128, false);
			if (!pkg.isEmpty() && !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
				throw new BootBusinessException("OPPO 应用包名无效");
			sqlMapper.updateOppoPkg(task, pkg);
			sqlMapper.updateSaveMonitorTask(enabled, task);
		}
		else
			throw new BootBusinessException("不支持的写操作");
	}

	/** 解绑后任务不再要求 RTA 准入；重新绑定会分配新的 OPPO 策略 ID。 */
	@Transactional
	public void deleteBinding(String monitorTaskId, long version) {
		List<Map<String, Object>> tasks = sqlMapper.lockTask(monitorTaskId);
		if (tasks.size() != 1)
			throw new BootBusinessException("任务不可用");
		scope.requireOwner(tasks.get(0).get("advertiser_id").toString());
		changed(sqlMapper.deleteRtaBinding(monitorTaskId, version));
		sqlMapper.updateSaveMonitorTask(false, monitorTaskId);
	}

	@Transactional
	public void deleteUpstream(String id, long version) {
		scope.requireSystemAdministrator();
		if (sqlMapper.findUpstream(id) == null)
			throw new BootBusinessException("接入不可用");
		Long used = sqlMapper.countUpstreamBindings(id);
		if (used != null && used > 0)
			throw new BootBusinessException("仍被 " + used + " 个任务使用，请先解绑");
		changed(sqlMapper.deleteRtaUpstream(id, version));
	}

	@Transactional
	public void deleteMediaAccess(String mediaId, long version) {
		scope.requireSystemAdministrator();
		if (sqlMapper.selectSaveRtaMediaAccess(mediaId).isEmpty())
			throw new BootBusinessException("媒体接入不可用");
		Long used = sqlMapper.countMediaBindings(mediaId);
		if (used != null && used > 0)
			throw new BootBusinessException("仍被 " + used + " 个任务使用，请先解绑");
		changed(sqlMapper.deleteRtaMediaAccess(mediaId, version));
	}

	public Page records(Map<String, String> query) {
		String advertiser = scope.constrain(query.get("advertiserId")), media = query.get("mediaId");
		long current = number(query.get("current"), 1, 1, 1000), size = number(query.get("size"), 30, 1, 100),
				now = System.currentTimeMillis();
		String key = RtaContract.PREFIX + "records:" + (advertiser != null ? "advertiser:" + advertiser
				: media != null && !media.isBlank() ? "media:" + media : "all");
		redis.opsForZSet().removeRangeByScore(key, 0, now - RtaContract.RETENTION_MS);
		List<Map<String, Object>> rows = new ArrayList<>();
		long matched = 0;
		for (long offset = 0;; offset += 500) {
			Set<String> ids = redis.opsForZSet().reverseRange(key, offset, offset + 499);
			if (ids == null || ids.isEmpty())
				break;
			List<String> values = redis.opsForValue()
				.multiGet(ids.stream().map(id -> RtaContract.PREFIX + "state:" + id).toList());
			if (values != null)
				for (String value : values) {
					if (value == null)
						continue;
					JSONObject state = JSON.parseObject(value);
					if (state.getString("originalRequestId") != null)
						state.put("requestId", state.getString("originalRequestId"));
					if (advertiser != null && !advertiser.equals(state.getString("advertiserId")))
						continue;
					if (media != null && !media.isBlank() && !media.equals(state.getString("mediaId")))
						continue;
					boolean matches = true;
					for (String f : List.of("requestId", "relayRequestId", "status", "deliveryStatus"))
						if (query.get(f) != null && !query.get(f).isBlank() && !query.get(f).equals(state.getString(f)))
							matches = false;
					String task = query.get("monitorTaskId");
					if (task != null && !task.isBlank()
							&& state.getJSONArray("bindings")
								.stream()
								.map(item -> JSON.parseObject(JSON.toJSONString(item)))
								.noneMatch(item -> task.equals(item.getString("taskId"))))
						matches = false;
					if (!matches)
						continue;
					if (matched++ < (current - 1) * size || rows.size() >= size)
						continue;
					Map<String, Object> row = new LinkedHashMap<>();
					for (String field : List.of("requestId", "relayRequestId", "mediaId", "advertiserId", "status",
							"deliveryStatus", "reasonCode", "receivedAt", "completedAt", "deadline", "expiresAt",
							"matchedTaskIds", "deliveryAttempts"))
						row.put(field, state.get(field));
					if (!(row.get("matchedTaskIds") instanceof List))
						row.put("matchedTaskIds", List.of());
					var upstream = state.getJSONObject("upstream");
					if (upstream != null) {
						String protocol = upstream.getString("protocol");
						if (protocol == null)
							protocol = "GENERIC_ASYNC";
						row.put("protocol", protocol);
						row.put("decisionMode", RtaProtocols.require(protocol).mode());
					}
					row.put("providerRequestId", state.getOrDefault("providerRequestId", state.get("relayRequestId")));
					row.put("dispatchStatus", state.get("dispatchStatus"));
					rows.add(row);
				}
			if (ids.size() < 500)
				break;
		}
		Set<Object> mediaIds = new HashSet<>();
		for (var row : rows)
			mediaIds.add(row.get("mediaId"));
		Map<String, String> names = new HashMap<>();
		if (!mediaIds.isEmpty())
			sqlMapper.selectMediaNames(mediaIds)
				.forEach(row -> names.put((String) row.get("id"), (String) row.get("media_name")));
		for (var row : rows)
			row.put("mediaName", names.getOrDefault(row.get("mediaId"), Objects.toString(row.get("mediaId"))));
		return new Page(rows, matched, current, size, null);
	}

	private Map<String, String> parseConfig(Object input) {
		if (input == null)
			return Map.of();
		Object value = input instanceof String ? JSON.parse(input.toString()) : input;
		if (!(value instanceof Map<?, ?> map))
			throw new BootBusinessException("协议配置必须是对象");
		Map<String, String> result = new LinkedHashMap<>();
		map.forEach((k, v) -> {
			if (!(k instanceof String) || !(v instanceof String))
				throw new BootBusinessException("协议参数必须是字符串");
			result.put((String) k, (String) v);
		});
		return result;
	}

	private void exists(Long count) {
		if (!Long.valueOf(1).equals(count))
			throw new BootBusinessException("对象不可用");
	}

	private void url(String value) {
		try {
			URI u = URI.create(value);
			if (!"https".equals(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null
					|| u.getRawQuery() != null || u.getFragment() != null || u.getPort() != -1 && u.getPort() != 443
					|| allowedHosts == null || !allowedHosts.contains(u.getHost().toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException();
		}
		catch (Exception e) {
			throw new BootBusinessException("地址必须是允许域名中的 HTTPS 地址，不能包含查询参数或凭据");
		}
	}

	private static String text(Map<String, Object> body, String name, int max, boolean required) {
		Object value = body.get(name);
		if (value != null && !(value instanceof String))
			throw new BootBusinessException("字段类型无效");
		String s = value == null ? "" : value.toString().trim();
		if (s.length() > max || required && s.isEmpty() || s.chars().anyMatch(Character::isISOControl))
			throw new BootBusinessException("字段无效：" + name);
		return s;
	}

	private static void identifier(String value) {
		if (!value.matches("[A-Za-z0-9_-]{1,64}"))
			throw new BootBusinessException("接入编号或凭据引用无效");
	}

	private static void changed(int n) {
		if (n != 1)
			throw new BootBusinessException(409, "配置已变更，请刷新后重试");
	}

	private static long number(String value, long fallback, long min, long max) {
		try {
			long n = value == null || value.isBlank() ? fallback : Long.parseLong(value);
			if (n < min || n > max)
				throw new NumberFormatException();
			return n;
		}
		catch (NumberFormatException e) {
			throw new BootBusinessException("数字参数越界");
		}
	}

	private Map<String, Object> camel(Map<String, Object> input) {
		Map<String, Object> out = new LinkedHashMap<>();
		input.forEach((key, value) -> {
			String[] parts = key.toLowerCase(Locale.ROOT).split("_");
			StringBuilder name = new StringBuilder(parts[0]);
			for (int i = 1; i < parts.length; i++)
				name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
			out.put(name.toString(), value instanceof java.sql.Timestamp ? value.toString() : value);
		});
		return out;
	}

}
