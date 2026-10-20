package com.yzytech.scx.ocpx.admin.rta;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.mapper.RtaManagementSqlMapper;
import com.yzytech.scx.ocpx.admin.service.AdvertiserScope;
import com.yzytech.scx.ocpx.rta.contract.RtaProtocols;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.sql.Timestamp;
import java.util.*;

/** 按监测任务汇总 RTA 三段配置（媒体接入、广告主接入、任务绑定）的就绪状态。 */
@Service
public class RtaReadinessService {

	static final int MAX_ROUTES = 2000;

	public enum Level {

		OK, WARN, BLOCK

	}

	public enum Status {

		READY, INCOMPLETE, DISABLED, NOT_CONFIGURED

	}

	public record Check(String code, String label, Level level, String hint, String fixTarget) {
	}

	public record Segment(String state, String label) {
	}

	public record Route(String monitorTaskId, String monitorName, String advertiserId, String advertiserName,
			String mediaId, String mediaName, String mediaProtocol, String upstreamId, String upstreamName,
			String upstreamProtocol, Integer strategyId, Status status, Segment media, Segment upstream,
			Segment binding, List<Check> checks, long requestCount24h, long allowCount24h) {
	}

	public record Summary(long ready, long incomplete, long disabled, long notConfigured) {
	}

	public record Overview(List<Route> records, long total, long current, long size, Summary summary,
			List<Check> notices) {
	}

	public record Readiness(Route route, List<Check> notices) {
	}

	private final RtaManagementSqlMapper sqlMapper;

	private final AdvertiserScope scope;

	private final String publicBaseUrl;

	private final List<String> allowedHosts;

	public RtaReadinessService(RtaManagementSqlMapper sqlMapper, AdvertiserScope scope,
			@Value("${app.rta-relay.public-base-url:}") String publicBaseUrl,
			@Value("${app.rta-relay.allowed-hosts:}") List<String> allowedHosts) {
		this.sqlMapper = sqlMapper;
		this.scope = scope;
		this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
		this.allowedHosts = allowedHosts == null ? List.of()
				: allowedHosts.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).toList();
	}

	public Overview overview(Map<String, String> query) {
		String advertiser = scope.constrain(query.get("advertiserId"));
		long current = number(query.get("current"), 1, 1, 1000), size = number(query.get("size"), 30, 1, 200);
		List<String> taskIds = ids(query.get("monitorTaskIds"));
		List<Route> routes = evaluate(sqlMapper.selectReadinessRoutes(advertiser, blank(query.get("mediaId")),
				blank(query.get("keyword")), taskIds.isEmpty() ? null : taskIds));
		Summary summary = new Summary(count(routes, Status.READY), count(routes, Status.INCOMPLETE),
				count(routes, Status.DISABLED), count(routes, Status.NOT_CONFIGURED));
		String status = blank(query.get("status"));
		boolean onlyIssues = "true".equals(query.get("onlyIssues"));
		List<Route> filtered = routes.stream()
			.filter(r -> status == null || r.status().name().equals(status))
			.filter(r -> !onlyIssues || r.status() == Status.INCOMPLETE
					|| r.checks().stream().anyMatch(c -> c.level() != Level.OK))
			.toList();
		int from = (int) Math.min(filtered.size(), (current - 1) * size);
		int to = (int) Math.min(filtered.size(), from + size);
		List<Route> page = withStats(filtered.subList(from, to));
		return new Overview(page, filtered.size(), current, size, summary, notices());
	}

	public Readiness readiness(String monitorTaskId) {
		String task = blank(monitorTaskId);
		if (task == null)
			throw new BootBusinessException("请指定监测任务");
		String advertiser = scope.constrain(null);
		List<Route> routes = evaluate(sqlMapper.selectReadinessRoutes(advertiser, null, null, List.of(task)));
		if (routes.size() != 1)
			throw new BootBusinessException("任务不可用");
		return new Readiness(withStats(routes).get(0), notices());
	}

	List<Route> evaluate(List<Map<String, Object>> rows) {
		if (rows == null)
			return List.of();
		return rows.stream().limit(MAX_ROUTES).map(this::evaluate).toList();
	}

	Route evaluate(Map<String, Object> row) {
		List<Check> checks = new ArrayList<>();
		boolean hasAccess = row.get("access_media_id") != null;
		boolean mediaEnabled = flag(row.get("media_enabled"));
		boolean hasBinding = row.get("binding_task_id") != null;
		boolean bindingEnabled = hasBinding && flag(row.get("binding_enabled")) && flag(row.get("rta_enabled"));
		boolean hasUpstream = row.get("upstream_ref_id") != null;
		boolean upstreamEnabled = flag(row.get("upstream_enabled"));
		String mediaProtocol = text(row.get("media_protocol"));
		String upstreamProtocol = text(row.get("upstream_protocol"));

		if (!hasAccess)
			checks.add(block("MEDIA_ACCESS_MISSING", "媒体未开通 RTA 接入", "在媒体管理中为该媒体配置 RTA 接入", "media"));
		else if (!mediaEnabled)
			checks.add(block("MEDIA_ACCESS_DISABLED", "媒体 RTA 接入已停用", "启用媒体的 RTA 接入", "media"));
		else
			checks.add(ok("MEDIA_ACCESS", "媒体 RTA 接入已启用", "media"));

		if (!hasBinding)
			checks.add(block("BINDING_MISSING", "任务未绑定广告主接入", "为任务选择广告主 RTA 接入并保存", "binding"));
		else if (!bindingEnabled)
			checks.add(block("BINDING_DISABLED", "任务 RTA 绑定已停用", "在任务 RTA 配置中启用绑定", "binding"));
		else
			checks.add(ok("BINDING", "任务 RTA 绑定已启用", "binding"));

		if (hasBinding) {
			if (!hasUpstream)
				checks.add(block("UPSTREAM_MISSING", "绑定的广告主接入不存在", "重新选择广告主 RTA 接入", "binding"));
			else if (!upstreamEnabled)
				checks.add(block("UPSTREAM_DISABLED", "广告主 RTA 接入已停用", "在广告主管理中启用该接入", "upstream"));
			else
				checks.add(ok("UPSTREAM", "广告主 RTA 接入已启用", "upstream"));
		}

		RtaProtocols.Definition protocol = protocol(upstreamProtocol);
		if (hasBinding && hasUpstream && hasAccess) {
			if ("OPPO".equals(mediaProtocol) && !"WEIFENG_RTA".equals(upstreamProtocol))
				checks.add(block("PROTOCOL_INCOMPATIBLE", "协议组合不受支持", "OPPO 媒体只能绑定微风 RTA 接入", "binding"));
		}
		if (hasBinding && protocol != null) {
			if (!protocol.bindingFields().isEmpty()
					&& (text(row.get("product_id")).isBlank() || text(row.get("external_task_id")).isBlank()))
				checks.add(block("MAPPING_MISSING", "上游产品或任务标识未填写", "在任务 RTA 配置中补齐映射", "binding"));
			if (!protocol.routeIdentityFields().isEmpty()) {
				try {
					protocol.validateRoute(text(row.get("link_url")), config(row.get("upstream_config")));
				}
				catch (IllegalArgumentException e) {
					checks.add(block("ROUTE_IDENTITY_MISMATCH", e.getMessage(), "确保推广链接与 RTA 接入的计划参数一致", "upstream"));
				}
			}
			if (!host(text(row.get("upstream_endpoint"))))
				checks.add(
						warn("UPSTREAM_HOST_NOT_ALLOWED", "广告主接入地址不在允许域名中", "在服务端配置 allowed-hosts 中加入该域名", "upstream"));
		}
		if (hasAccess && "OPPO".equals(mediaProtocol) && hasBinding && text(row.get("oppo_pkg")).isBlank())
			checks.add(warn("OPPO_PKG_MISSING", "未填写投放应用包名", "OPPO 转化回传需要包名", "binding"));
		if (hasAccess && !"OPPO".equals(mediaProtocol) && !host(text(row.get("notify_url"))))
			checks.add(warn("NOTIFY_HOST_NOT_ALLOWED", "媒体决策通知地址不在允许域名中", "在服务端配置 allowed-hosts 中加入该域名", "media"));

		Status status;
		if (!hasBinding)
			status = Status.NOT_CONFIGURED;
		else if (!bindingEnabled)
			status = Status.DISABLED;
		else if (checks.stream().anyMatch(c -> c.level() == Level.BLOCK))
			status = Status.INCOMPLETE;
		else
			status = Status.READY;

		return new Route(text(row.get("task_id")), text(row.get("task_name")), text(row.get("advertiser_id")),
				text(row.get("advertiser_name")), text(row.get("media_id")), text(row.get("media_name")),
				hasAccess ? mediaProtocol : null, hasBinding ? text(row.get("upstream_id")) : null,
				hasUpstream ? text(row.get("upstream_name")) : null, hasUpstream ? upstreamProtocol : null,
				row.get("strategy_id") instanceof Number n ? n.intValue() : null, status,
				segment(hasAccess, mediaEnabled), segment(hasUpstream, upstreamEnabled),
				segment(hasBinding, bindingEnabled), List.copyOf(checks), 0, 0);
	}

	List<Check> notices() {
		List<Check> notices = new ArrayList<>();
		if (publicBaseUrl.isEmpty() || publicBaseUrl.contains("example.invalid"))
			notices
				.add(warn("PUBLIC_BASE_URL_PLACEHOLDER", "对外接入地址仍是占位值", "在服务端配置 app.rta-relay.public-base-url", null));
		notices.add(warn("ENGINE_CONFIG_UNVERIFIED", "引擎开关与凭据无法在管理端确认",
				"确认 Engine 配置中 app.rta-relay.enabled、credentials 与 payload-key 已正确填写", null));
		return List.copyOf(notices);
	}

	private List<Route> withStats(List<Route> routes) {
		if (routes.isEmpty())
			return routes;
		Map<String, long[]> stats = new HashMap<>();
		var rows = sqlMapper.selectRecentTaskStats(new Timestamp(System.currentTimeMillis() - 86_400_000L),
				routes.stream().map(Route::monitorTaskId).toList());
		if (rows != null)
			for (var row : rows)
				stats.put(text(row.get("task_id")),
						new long[] { longValue(row.get("request_count")), longValue(row.get("allow_count")) });
		return routes.stream().map(r -> {
			long[] s = stats.getOrDefault(r.monitorTaskId(), new long[2]);
			return new Route(r.monitorTaskId(), r.monitorName(), r.advertiserId(), r.advertiserName(), r.mediaId(),
					r.mediaName(), r.mediaProtocol(), r.upstreamId(), r.upstreamName(), r.upstreamProtocol(),
					r.strategyId(), r.status(), r.media(), r.upstream(), r.binding(), r.checks(), s[0], s[1]);
		}).toList();
	}

	private boolean host(String url) {
		try {
			String host = URI.create(url).getHost();
			return host != null && allowedHosts.contains(host.toLowerCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static RtaProtocols.Definition protocol(String code) {
		try {
			return code.isEmpty() ? null : RtaProtocols.require(code);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Map<String, String> config(Object value) {
		if (value == null)
			return Map.of();
		Map<String, String> result = new HashMap<>();
		var parsed = JSON.parseObject(value.toString());
		if (parsed != null)
			parsed.forEach((k, v) -> result.put(k, v == null ? null : v.toString()));
		return result;
	}

	private static Segment segment(boolean exists, boolean enabled) {
		return !exists ? new Segment("MISSING", "未配置")
				: enabled ? new Segment("ENABLED", "已启用") : new Segment("DISABLED", "已停用");
	}

	private static Check ok(String code, String label, String target) {
		return new Check(code, label, Level.OK, null, target);
	}

	private static Check warn(String code, String label, String hint, String target) {
		return new Check(code, label, Level.WARN, hint, target);
	}

	private static Check block(String code, String label, String hint, String target) {
		return new Check(code, label, Level.BLOCK, hint, target);
	}

	private static long count(List<Route> routes, Status status) {
		return routes.stream().filter(r -> r.status() == status).count();
	}

	private static boolean flag(Object value) {
		if (value instanceof Boolean b)
			return b;
		return value instanceof Number n && n.intValue() == 1;
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static long longValue(Object value) {
		return value instanceof Number n ? n.longValue() : 0;
	}

	private static List<String> ids(String value) {
		if (value == null || value.isBlank())
			return List.of();
		List<String> ids = Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.distinct()
			.toList();
		if (ids.size() > 200 || ids.stream().anyMatch(id -> id.length() > 32))
			throw new BootBusinessException("任务参数无效");
		return ids;
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

}
