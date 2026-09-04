package com.yzytech.scx.ocpx.admin.service;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.dto.RtaAdminRequests;
import com.yzytech.scx.ocpx.admin.dto.RtaAdminViews;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** RTA Profile、监测任务绑定、决策事实和统计的管理面服务。 */
@Service
@RequiredArgsConstructor
public class OcpxRtaAdminService {

	private final JdbcTemplate jdbcTemplate;

	private final AdvertiserScope advertiserScope;

	private final OcpxConfigRefreshPublisher refreshPublisher;

	@Value("${app.rta.mock-enabled:false}")
	private boolean mockRtaEnabled;

	@Value("${app.rta.enabled:false}")
	private boolean rtaEnabled;

	public RtaAdminViews.ProfilePage profiles(String keyword, Long currentValue, Long sizeValue) {
		long current = currentValue == null ? 1 : Math.max(1, currentValue);
		long size = sizeValue == null ? 30 : Math.min(100, Math.max(1, sizeValue));
		String normalized = keyword == null ? "" : keyword.trim();
		String where = normalized.isEmpty() ? "" : " WHERE code LIKE ? OR name LIKE ?";
		Object[] filterArgs = normalized.isEmpty() ? new Object[0]
				: new Object[] { "%" + normalized + "%", "%" + normalized + "%" };
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_rta_profile" + where, Long.class,
				filterArgs);
		List<Object> args = new ArrayList<>();
		for (Object filterArg : filterArgs) {
			args.add(filterArg);
		}
		args.add(size);
		args.add((current - 1) * size);
		List<RtaAdminViews.Profile> records = jdbcTemplate.query("""
				SELECT id,code,name,adapter_code,status,config_json,version,create_time,update_time
				FROM scx_ocpx_rta_profile
				""" + where + " ORDER BY update_time DESC,id DESC LIMIT ? OFFSET ?", (rs, rowNum) -> new RtaAdminViews.Profile(
				rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getString("adapter_code"),
				rs.getString("status"), safeMockConfig(rs.getString("config_json")), rs.getInt("version"), time(rs.getTimestamp("create_time")),
				time(rs.getTimestamp("update_time"))), args.toArray());
		return new RtaAdminViews.ProfilePage(records, current, size, total == null ? 0 : total);
	}

	public RtaAdminViews.Profile profile(String id) {
		List<RtaAdminViews.Profile> values = jdbcTemplate.query("""
				SELECT id,code,name,adapter_code,status,config_json,version,create_time,update_time
				FROM scx_ocpx_rta_profile WHERE id=?
				""", (rs, rowNum) -> new RtaAdminViews.Profile(rs.getString("id"), rs.getString("code"),
					rs.getString("name"), rs.getString("adapter_code"), rs.getString("status"), safeMockConfig(rs.getString("config_json")),
					rs.getInt("version"), time(rs.getTimestamp("create_time")), time(rs.getTimestamp("update_time"))), id);
		if (values.isEmpty()) {
			throw new BootBusinessException(404, "RTA Profile 不存在");
		}
		return values.get(0);
	}

	@Transactional
	public RtaAdminViews.Profile createProfile(RtaAdminRequests.Profile request) {
		advertiserScope.requireAdministrator();
		validateProfile(request);
		String id = id();
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_rta_profile(id,code,name,adapter_code,status,config_json,version,create_time,update_time)
				VALUES (?,?,?,?,?,?,1,NOW(3),NOW(3))
				""", id, request.getCode().trim(), request.getName().trim(), request.getAdapterCode(), request.getStatus(),
				normalizedConfig(request.getConfigJson()));
		refreshPublisher.mark();
		return profile(id);
	}

	@Transactional
	public RtaAdminViews.Profile updateProfile(String id, RtaAdminRequests.Profile request) {
		advertiserScope.requireAdministrator();
		validateProfile(request);
		profile(id);
		jdbcTemplate.update("""
				UPDATE scx_ocpx_rta_profile
				SET code=?,name=?,adapter_code=?,status=?,config_json=?,version=version+1,update_time=NOW(3)
				WHERE id=?
				""", request.getCode().trim(), request.getName().trim(), request.getAdapterCode(), request.getStatus(),
				normalizedConfig(request.getConfigJson()), id);
		refreshPublisher.mark();
		return profile(id);
	}

	@Transactional
	public void updateTaskConfig(String taskId, RtaAdminRequests.TaskConfig request) {
		if (request == null || request.getEnabled() == null) {
			throw new BootBusinessException("RTA 任务配置不能为空");
		}
		TaskRow task = requireTask(taskId);
		advertiserScope.requireOwner(task.advertiserId());
		if (Boolean.TRUE.equals(request.getEnabled())) {
			if (!rtaEnabled || !mockRtaEnabled) {
				throw new BootBusinessException(409, "RTA_ACTIVE_BLOCKED: 当前运行环境未开启 Mock RTA");
			}
			if (!org.springframework.util.StringUtils.hasText(request.getProfileId())) {
				throw new BootBusinessException("启用 RTA 前必须选择 Profile");
			}
			if (request.getBindings() == null || request.getBindings().isEmpty()) {
				throw new BootBusinessException("启用 RTA 前必须绑定至少一个外部任务");
			}
			RtaAdminViews.Profile profile = profile(request.getProfileId());
			if (!"ACTIVE".equals(profile.getStatus()) || !"MOCK".equals(profile.getAdapterCode())) {
				throw new BootBusinessException(409, "RTA_ACTIVE_BLOCKED: 当前仅支持 Mock 适配器");
			}
		}
		if (request.getBindings() != null && request.getBindings().size() > 100) {
			throw new BootBusinessException("外部任务绑定数量超过上限");
		}
		Set<String> externalTaskIds = new HashSet<>();
		if (request.getBindings() != null) {
			for (RtaAdminRequests.Binding binding : request.getBindings()) {
				if (binding != null && org.springframework.util.StringUtils.hasText(binding.getExternalTaskId())
						&& !externalTaskIds.add(binding.getExternalTaskId().trim())) {
					throw new BootBusinessException("同一 Profile 下不能重复绑定外部任务");
				}
			}
		}
		jdbcTemplate.update("UPDATE scx_ocpx_monitor_task SET rta_enabled=?,update_time=NOW(3) WHERE id=?",
				request.getEnabled(), taskId);
		jdbcTemplate.update("DELETE FROM scx_ocpx_rta_task_binding WHERE monitor_task_id=?", taskId);
		if (org.springframework.util.StringUtils.hasText(request.getProfileId()) && request.getBindings() != null) {
			for (RtaAdminRequests.Binding binding : request.getBindings()) {
				if (binding == null || !org.springframework.util.StringUtils.hasText(binding.getExternalTaskId())) {
					throw new BootBusinessException("外部任务 ID 不能为空");
				}
				jdbcTemplate.update("""
						INSERT INTO scx_ocpx_rta_task_binding
						(id,monitor_task_id,advertiser_id,promotion_link_id,media_id,profile_id,external_task_id,
						 external_product_id,enabled,version,create_time,update_time)
						VALUES (?,?,?,?,?,?,?, ?,?,1,NOW(3),NOW(3))
						""", id(), taskId, task.advertiserId(), task.promotionLinkId(), task.mediaId(), request.getProfileId(),
						binding.getExternalTaskId().trim(), trim(binding.getExternalProductId()), true);
			}
		}
		refreshPublisher.mark();
	}

	public RtaAdminViews.TaskConfig taskConfig(String taskId) {
		TaskRow task = requireTask(taskId);
		advertiserScope.requireOwner(task.advertiserId());
		List<RtaAdminViews.Binding> bindings = bindings(taskId);
		String profileId = bindings.isEmpty() ? null : bindings.get(0).getProfileId();
		return new RtaAdminViews.TaskConfig(taskId, task.rtaEnabled(), profileId, bindings);
	}

	public RtaAdminViews.DecisionPage decisions(RtaAdminRequests.Query request) {
		DateRange range = range(request);
		String scopedAdvertiser = advertiserScope.constrain(request.getAdvertiserId());
		List<Object> args = new ArrayList<>();
		StringBuilder where = new StringBuilder(" WHERE f.received_at>=? AND f.received_at<?");
		args.add(range.start().atStartOfDay());
		args.add(range.end().plusDays(1).atStartOfDay());
		filter(where, args, "f.advertiser_id", scopedAdvertiser);
		filter(where, args, "f.media_id", request.getMediaId());
		filter(where, args, "f.promotion_link_id", request.getPromotionLinkId());
		filter(where, args, "f.monitor_task_id", request.getMonitorTaskId());
		filter(where, args, "f.profile_id", request.getProfileId());
		long size = pageSize(request.getSize());
		long current = pageCurrent(request.getCurrent());
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_rta_decision_fact f" + where,
				Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size);
		pageArgs.add((current - 1) * size);
		List<RtaAdminViews.Decision> records = jdbcTemplate.query("""
				SELECT f.id,f.request_id,f.advertiser_id,f.promotion_link_id,f.media_id,f.monitor_task_id,f.profile_id,p.name,
				       f.decision,f.outcome_class,f.reason_code,f.latency_ms,f.received_at
				FROM scx_ocpx_rta_decision_fact f
				LEFT JOIN scx_ocpx_rta_profile p ON p.id=f.profile_id
				""" + where + " ORDER BY f.received_at DESC,f.id DESC LIMIT ? OFFSET ?", (rs, rowNum) -> new RtaAdminViews.Decision(
				rs.getString("id"), rs.getString("request_id"), rs.getString("advertiser_id"), rs.getString("media_id"),
				 rs.getString("promotion_link_id"), rs.getString("monitor_task_id"), rs.getString("profile_id"), rs.getString("name"), rs.getString("decision"),
				rs.getString("outcome_class"), rs.getString("reason_code"), rs.getLong("latency_ms"), time(rs.getTimestamp("received_at"))),
			pageArgs.toArray());
		return new RtaAdminViews.DecisionPage(records, current, size, total == null ? 0 : total);
	}

	public RtaAdminViews.MetricPage metrics(RtaAdminRequests.Query request) {
		DateRange range = range(request);
		String scopedAdvertiser = advertiserScope.constrain(request.getAdvertiserId());
		String bucket = "HOUR".equals(request.getGrain()) ? "m.time_hour" : "TIMESTAMP(DATE(m.time_hour))";
		StringBuilder where = new StringBuilder(" WHERE m.time_hour>=? AND m.time_hour<?");
		List<Object> args = new ArrayList<>();
		args.add(range.start().atStartOfDay());
		args.add(range.end().plusDays(1).atStartOfDay());
		filter(where, args, "m.advertiser_id", scopedAdvertiser);
		filter(where, args, "m.media_id", request.getMediaId());
		filter(where, args, "m.promotion_link_id", request.getPromotionLinkId());
		filter(where, args, "m.monitor_task_id", request.getMonitorTaskId());
		filter(where, args, "m.profile_id", request.getProfileId());
		List<RtaAdminViews.Metric> records = jdbcTemplate.query("""
				SELECT %s AS bucket_start,m.advertiser_id,m.promotion_link_id,m.media_id,m.monitor_task_id,m.profile_id,p.name,
				       SUM(m.request_count),SUM(m.allow_count),SUM(m.deny_count),SUM(m.timeout_count),SUM(m.error_count),
				       CASE WHEN SUM(m.request_count)=0 THEN NULL ELSE SUM(m.allow_count)/SUM(m.request_count) END
				FROM scx_ocpx_rta_hourly_metric m
				LEFT JOIN scx_ocpx_rta_profile p ON p.id=m.profile_id
				%s
				GROUP BY %s,m.advertiser_id,m.promotion_link_id,m.media_id,m.monitor_task_id,m.profile_id,p.name
				ORDER BY bucket_start DESC
				""".formatted(bucket, where, bucket), (rs, rowNum) -> new RtaAdminViews.Metric(time(rs.getTimestamp("bucket_start")),
					 rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8),
					 rs.getLong(9), rs.getLong(10), rs.getLong(11), rs.getLong(12), rs.getBigDecimal(13)), args.toArray());
		String asOf = jdbcTemplate.queryForObject("SELECT DATE_FORMAT(MAX(update_time),'%Y-%m-%dT%H:%i:%s') FROM scx_ocpx_rta_hourly_metric",
				String.class);
		return new RtaAdminViews.MetricPage(records, asOf);
	}

	private List<RtaAdminViews.Binding> bindings(String taskId) {
		return jdbcTemplate.query("""
				SELECT b.id,b.monitor_task_id,b.profile_id,p.name,b.external_task_id,b.external_product_id,b.enabled
				FROM scx_ocpx_rta_task_binding b JOIN scx_ocpx_rta_profile p ON p.id=b.profile_id
				WHERE b.monitor_task_id=? ORDER BY b.id
				""", (rs, rowNum) -> new RtaAdminViews.Binding(rs.getString(1), rs.getString(2), rs.getString(3),
					rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7)), taskId);
	}

	private TaskRow requireTask(String taskId) {
		List<TaskRow> rows = jdbcTemplate.query("""
				SELECT id,advertiser_id,promotion_link_id,media_id,rta_enabled
				FROM scx_ocpx_monitor_task WHERE id=? AND is_delete=0
				""", (rs, rowNum) -> new TaskRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
					rs.getBoolean(5)), taskId);
		if (rows.size() != 1) {
			throw new BootBusinessException(404, "监测任务不存在");
		}
		return rows.get(0);
	}

	private void validateProfile(RtaAdminRequests.Profile request) {
		if (request == null || !"MOCK".equals(request.getAdapterCode())) {
			throw new BootBusinessException(409, "本期只允许 MOCK 适配器");
		}
		if (request.getConfigJson() != null && request.getConfigJson().length() > 4096) {
			throw new BootBusinessException("Mock 配置过长");
		}
		if (request.getConfigJson() != null && !request.getConfigJson().isBlank()) {
			if (request.getConfigJson().matches("(?is).*\\\"(?:url|endpoint|secret|token|password|access[_-]?key|api[_-]?key)\\\"\\s*:.*")) {
				throw new BootBusinessException("Mock 配置不得包含供应商地址或认证密钥");
			}
			try {
				com.alibaba.fastjson2.JSONObject config = JSON.parseObject(request.getConfigJson());
				Set<String> allowedKeys = Set.of("decision", "delayMs", "taskIds");
				if (config.keySet().stream().anyMatch(key -> !allowedKeys.contains(key))) {
					throw new BootBusinessException("Mock 配置只允许 decision、delayMs、taskIds");
				}
				String decision = config.getString("decision");
				if (decision != null && !Set.of("ALLOW", "DENY", "ERROR").contains(decision.toUpperCase())) {
					throw new BootBusinessException("Mock decision 只允许 ALLOW、DENY 或 ERROR");
				}
				if (config.containsKey("delayMs") && config.getLongValue("delayMs") > 10_000) {
					throw new BootBusinessException("Mock delayMs 不能超过 10000");
				}
				if (config.containsKey("taskIds") && config.getList("taskIds", String.class) == null) {
					throw new BootBusinessException("Mock taskIds 必须是字符串数组");
				}
			}
			catch (BootBusinessException exception) {
				throw exception;
			}
			catch (RuntimeException exception) {
				throw new BootBusinessException("Mock 配置必须是合法 JSON");
			}
		}
	}

	private String normalizedConfig(String config) {
		return config == null || config.isBlank() ? "{}" : config.trim();
	}

	/** Profile responses expose only Mock controls, even if an old row contains extra keys. */
	private String safeMockConfig(String value) {
		if (value == null || value.isBlank()) {
			return "{}";
		}
		try {
			com.alibaba.fastjson2.JSONObject source = JSON.parseObject(value);
			com.alibaba.fastjson2.JSONObject safe = new com.alibaba.fastjson2.JSONObject();
			if (source.containsKey("decision")) {
				safe.put("decision", source.getString("decision"));
			}
			if (source.containsKey("delayMs")) {
				safe.put("delayMs", Math.max(0, Math.min(10_000, source.getLongValue("delayMs"))));
			}
			if (source.containsKey("taskIds")) {
				List<String> taskIds = source.getList("taskIds", String.class);
				if (taskIds != null) {
					safe.put("taskIds", taskIds.stream().filter(item -> item != null && item.length() <= 128).distinct()
						.limit(100).toList());
				}
			}
			return JSON.toJSONString(safe);
		}
		catch (RuntimeException exception) {
			return "{}";
		}
	}

	private DateRange range(RtaAdminRequests.Query request) {
		LocalDate end = request.getEndDate() == null ? LocalDate.now() : request.getEndDate();
		LocalDate start = request.getStartDate() == null ? end.minusDays(6) : request.getStartDate();
		if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
			throw new BootBusinessException("RTA 统计时间范围无效");
		}
		return new DateRange(start, end);
	}

	private void filter(StringBuilder where, List<Object> args, String column, String value) {
		if (org.springframework.util.StringUtils.hasText(value)) {
			where.append(" AND ").append(column).append("=?");
			args.add(value.trim());
		}
	}

	private long pageSize(Long value) {
		return value == null ? 30 : Math.min(100, Math.max(1, value));
	}

	private long pageCurrent(Long value) {
		return value == null ? 1 : Math.max(1, value);
	}

	private LocalDateTime time(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private String trim(String value) {
		return org.springframework.util.StringUtils.hasText(value) ? value.trim() : null;
	}

	private String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private record TaskRow(String id, String advertiserId, String promotionLinkId, String mediaId,
			boolean rtaEnabled) {
	}

	private record DateRange(LocalDate start, LocalDate end) {
	}

}
