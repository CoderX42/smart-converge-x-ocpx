package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.ocpx.admin.dto.EventRequests;
import com.yzytech.scx.ocpx.admin.dto.EventViews;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OcpxEventService {

	private static final List<Seed> UPSTREAM_SEEDS = List.of(new Seed("ACTIVATE", "激活"), new Seed("ACQUISITION", "拉新"),
			new Seed("REGISTER", "注册"), new Seed("FIRST_REACTIVATION", "首唤"), new Seed("REACTIVATION_COMMON", "拉活（常用）"),
			new Seed("ALIPAY_ASSIST_SUCCESS_5S", "支付宝预算“助力成功&5s播放”"), new Seed("PAYMENT", "付费"),
			new Seed("NEXT_DAY_RETENTION", "次留"), new Seed("RETENTION_D3", "3日留存"), new Seed("RETENTION_D4", "4日留存"),
			new Seed("RETENTION_D5", "5日留存"), new Seed("RETENTION_D6", "6日留存"), new Seed("RETENTION_D7", "7日留存"),
			new Seed("APP_VISIT", "APP内访问"), new Seed("RECALL", "召回"), new Seed("KEY_ACTION", "关键行为"),
			new Seed("FIRST_ORDER", "首购"), new Seed("REACTIVATION", "拉活"), new Seed("VALID_EVOKE", "有效唤端"),
			new Seed("ORDER", "下单"), new Seed("LEAD", "线索"), new Seed("BUDGET_UNDEFINED", "预算未定义"));

	private static final List<Seed> MEDIA_SEEDS = List.of(new Seed("ACTIVATE", "激活"), new Seed("REGISTER", "注册"),
			new Seed("NEXT_DAY_RETENTION", "次留"), new Seed("REACTIVATION", "拉活/唤醒/唤起/唤端"),
			new Seed("FIRST_ACTIVATION", "首活首唤"), new Seed("FIRST_ORDER", "首单/首购"), new Seed("COMPLETE_ORDER", "完单"),
			new Seed("PAYMENT", "购买/付费"), new Seed("ORDER", "下单"), new Seed("VIDEO_COMPLETE_90S", "完播90秒"),
			new Seed("KEY_ACTION", "自定义事件/关键行为"), new Seed("RETENTION_D2", "2日留存"), new Seed("RETENTION_D3", "3日留存"),
			new Seed("RETENTION_D4", "4日留存"), new Seed("RETENTION_D5", "5日留存"), new Seed("RETENTION_D6", "6日留存"),
			new Seed("RETENTION_D7", "7日留存"), new Seed("FORM_SUBMIT", "表单提交"),
			new Seed("KEY_PAGE_VIEW", "关键页面行为/关键页面浏览"), new Seed("AUTHENTICATION", "授信/授权"), new Seed("RECALL", "拉回"),
			new Seed("UNKNOWN", "未知"));

	private static final Set<String> DEFAULT_MAPPING_CODES = Set.of("ACTIVATE", "REGISTER", "PAYMENT",
			"NEXT_DAY_RETENTION");

	private final JdbcTemplate jdbcTemplate;

	private final OcpxAccessScope accessScope;

	public OcpxEventService(JdbcTemplate jdbcTemplate, OcpxAccessScope accessScope) {
		this.jdbcTemplate = jdbcTemplate;
		this.accessScope = accessScope;
	}

	public Page<EventViews.Definition> definitions(long current, long size, String category, String channelId,
			String status, String keyword) {
		long safeCurrent = Math.max(1, current);
		long safeSize = Math.min(200, Math.max(1, size));
		StringBuilder where = new StringBuilder(" WHERE e.deleted=0 ");
		List<Object> args = new ArrayList<>();
		append(where, args, " AND e.category=?", category);
		if (StringUtils.hasText(channelId)) {
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		append(where, args, " AND e.channel_id=?", channelId);
		append(where, args, " AND e.status=?", status);
		appendChannelDefinitionScope(where, args, OcpxAccessScope.PermissionLevel.VIEW);
		if (StringUtils.hasText(keyword)) {
			where.append(" AND (e.event_code LIKE ? OR e.event_name LIKE ?)");
			args.add("%" + keyword.trim() + "%");
			args.add("%" + keyword.trim() + "%");
		}
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_event_definition e" + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(safeSize);
		pageArgs.add((safeCurrent - 1) * safeSize);
		List<EventViews.Definition> records = jdbcTemplate.query("""
				SELECT e.id,e.category,NULLIF(e.channel_id,''),c.channel_name,e.event_code,e.event_name,e.status,
				       e.sort_order,e.remark,e.built_in,
				       EXISTS(SELECT 1 FROM scx_ocpx_channel_event_mapping x WHERE x.deleted=0 AND
				         (x.upstream_event_id=e.id OR x.conversion_event_id=e.id OR x.media_event_id=e.id)),
				       (SELECT COUNT(*) FROM scx_ocpx_monitor_event_binding b
				          JOIN scx_ocpx_channel_event_mapping m ON m.id=b.event_mapping_id
				        WHERE m.upstream_event_id=e.id OR m.conversion_event_id=e.id OR m.media_event_id=e.id),
				      (SELECT COUNT(*) FROM scx_ocpx_route_event_snapshot s
				       WHERE (e.category='UPSTREAM' AND s.upstream_event_code=e.event_code AND s.monitor_id IN
				             (SELECT id FROM scx_ocpx_monitor_link WHERE channel_id=e.channel_id))
				          OR (e.category='CONVERSION' AND s.conversion_event_code=e.event_code)
				           OR (e.category='MEDIA' AND s.media_event_code=e.event_code AND s.monitor_id IN
				              (SELECT id FROM scx_ocpx_monitor_link WHERE channel_id=e.channel_id))),
				       e.create_time,e.update_time
				FROM scx_ocpx_event_definition e
				LEFT JOIN scx_ocpx_channel c ON c.id=e.channel_id
				""" + where + " ORDER BY e.sort_order,e.event_code LIMIT ? OFFSET ?", this::definition,
				pageArgs.toArray());
		Page<EventViews.Definition> result = new Page<>(safeCurrent, safeSize, total == null ? 0 : total);
		result.setRecords(records);
		return result;
	}

	@Transactional
	public EventViews.Definition saveDefinition(String id, EventRequests.SaveDefinition request) {
		String category = request.category().trim().toUpperCase();
		String channelId = "CONVERSION".equals(category) ? "" : requireChannel(request.channelId());
		requireDefinitionScope(category, channelId, OcpxAccessScope.PermissionLevel.MANAGE);
		String eventCode = "CONVERSION".equals(category) ? request.eventCode().trim().toUpperCase()
				: request.eventCode().trim();
		if (StringUtils.hasText(id)) {
			DefinitionRow existing = requireDefinition(id);
			requireDefinitionScope(existing.category(), existing.channelId(), OcpxAccessScope.PermissionLevel.MANAGE);
			if ("DISABLED".equals(request.status()) && draftReferenceCount(id) > 0) {
				throw new IllegalArgumentException("事件仍被监测链接草稿引用，请先移除绑定");
			}
			if ((!existing.eventCode().equals(eventCode) || !existing.category().equals(category)
					|| !existing.channelId().equals(channelId)) && referenceCount(id) > 0) {
				throw new IllegalArgumentException("事件已被引用，类别、渠道和事件码不可修改");
			}
			ensureUniqueDefinition(id, category, channelId, eventCode);
			jdbcTemplate.update("""
					UPDATE scx_ocpx_event_definition SET category=?,channel_id=?,event_code=?,event_name=?,status=?,
					 sort_order=?,remark=?,update_time=NOW(3) WHERE id=? AND deleted=0
					""", category, channelId, eventCode, request.eventName().trim(), request.status(),
					request.sortOrder(), trim(request.remark()), id);
			return definitionById(id);
		}
		ensureUniqueDefinition(null, category, channelId, eventCode);
		String newId = id();
		jdbcTemplate.update(
				"""
						INSERT INTO scx_ocpx_event_definition
						(id,category,channel_id,event_code,event_name,status,sort_order,remark,built_in,deleted,create_time,update_time)
						VALUES (?,?,?,?,?,?,?,?,0,0,NOW(3),NOW(3))
						""",
				newId, category, channelId, eventCode, request.eventName().trim(), request.status(),
				request.sortOrder(), trim(request.remark()));
		return definitionById(newId);
	}

	@Transactional
	public void disableDefinition(String id) {
		DefinitionRow definition = requireDefinition(id);
		requireDefinitionScope(definition.category(), definition.channelId(), OcpxAccessScope.PermissionLevel.MANAGE);
		if (draftReferenceCount(id) > 0) {
			throw new IllegalArgumentException("事件仍被监测链接草稿引用，请先移除绑定");
		}
		jdbcTemplate.update("UPDATE scx_ocpx_event_definition SET status='DISABLED',update_time=NOW(3) WHERE id=?", id);
		jdbcTemplate.update("""
				UPDATE scx_ocpx_channel_event_mapping SET status='DISABLED',update_time=NOW(3)
				WHERE upstream_event_id=? OR conversion_event_id=? OR media_event_id=?
				""", id, id, id);
	}

	public Page<EventViews.Mapping> mappings(long current, long size, String channelId, String status, String keyword) {
		long safeCurrent = Math.max(1, current);
		long safeSize = Math.min(200, Math.max(1, size));
		StringBuilder where = new StringBuilder(" WHERE m.deleted=0 ");
		List<Object> args = new ArrayList<>();
		append(where, args, " AND m.channel_id=?", channelId);
		if (StringUtils.hasText(channelId)) {
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		appendChannelScope(where, args, "m.channel_id", OcpxAccessScope.PermissionLevel.VIEW);
		append(where, args, " AND m.status=?", status);
		if (StringUtils.hasText(keyword)) {
			where.append(
					" AND (u.event_code LIKE ? OR u.event_name LIKE ? OR v.event_name LIKE ? OR d.event_name LIKE ?)");
			for (int i = 0; i < 4; i++) {
				args.add("%" + keyword.trim() + "%");
			}
		}
		String joins = """
				 FROM scx_ocpx_channel_event_mapping m
				 JOIN scx_ocpx_channel c ON c.id=m.channel_id
				 JOIN scx_ocpx_event_definition u ON u.id=m.upstream_event_id
				 JOIN scx_ocpx_event_definition v ON v.id=m.conversion_event_id
				 JOIN scx_ocpx_event_definition d ON d.id=m.media_event_id
				""";
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + joins + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(safeSize);
		pageArgs.add((safeCurrent - 1) * safeSize);
		List<EventViews.Mapping> records = jdbcTemplate.query("""
				SELECT m.id,m.channel_id,c.channel_name,u.id,u.event_code,u.event_name,v.id,v.event_code,v.event_name,
				 d.id,d.event_code,d.event_name,m.status,m.remark,
				 (SELECT COUNT(*) FROM scx_ocpx_monitor_event_binding b WHERE b.event_mapping_id=m.id),
				 (SELECT COUNT(*) FROM scx_ocpx_route_event_snapshot s WHERE s.event_mapping_id=m.id),
				 m.create_time,m.update_time
				""" + joins + where + " ORDER BY u.sort_order,u.event_code LIMIT ? OFFSET ?", this::mapping,
				pageArgs.toArray());
		Page<EventViews.Mapping> result = new Page<>(safeCurrent, safeSize, total == null ? 0 : total);
		result.setRecords(records);
		return result;
	}

	@Transactional
	public EventViews.Mapping saveMapping(String id, EventRequests.SaveMapping request) {
		String channelId = requireChannel(request.channelId());
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.MANAGE);
		DefinitionRow upstream = requireDefinition(request.upstreamEventId());
		DefinitionRow conversion = requireDefinition(request.conversionEventId());
		DefinitionRow media = requireDefinition(request.mediaEventId());
		if (!"UPSTREAM".equals(upstream.category()) || !channelId.equals(upstream.channelId())
				|| !"CONVERSION".equals(conversion.category()) || !conversion.channelId().isEmpty()
				|| !"MEDIA".equals(media.category()) || !channelId.equals(media.channelId())) {
			throw new IllegalArgumentException("事件类别或渠道不匹配");
		}
		if (!"ACTIVE".equals(upstream.status()) || !"ACTIVE".equals(conversion.status())
				|| !"ACTIVE".equals(media.status())) {
			throw new IllegalArgumentException("映射只能使用启用事件");
		}
		Long duplicate = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_channel_event_mapping
				WHERE channel_id=? AND upstream_event_id=? AND deleted=0 AND (? IS NULL OR id<>?)
				""", Long.class, channelId, upstream.id(), id, id);
		if (duplicate != null && duplicate > 0) {
			throw new IllegalArgumentException("该渠道的上游事件已存在映射");
		}
		String mappingId = StringUtils.hasText(id) ? id : id();
		if (StringUtils.hasText(id)) {
			MappingRow existing = requireMapping(id);
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, existing.channelId(),
					OcpxAccessScope.PermissionLevel.MANAGE);
			long draftReferences = draftMappingReferences(id);
			if ("DISABLED".equals(request.status()) && draftReferences > 0) {
				throw new IllegalArgumentException("映射仍被监测链接草稿引用，请先移除绑定");
			}
			if (draftReferences > 0
					&& (!existing.upstreamEventId().equals(upstream.id()) || !existing.channelId().equals(channelId))) {
				throw new IllegalArgumentException("映射已被草稿引用，不可修改渠道或上游事件");
			}
			jdbcTemplate.update("""
					UPDATE scx_ocpx_channel_event_mapping SET channel_id=?,upstream_event_id=?,conversion_event_id=?,
					 media_event_id=?,status=?,remark=?,update_time=NOW(3) WHERE id=? AND deleted=0
					""", channelId, upstream.id(), conversion.id(), media.id(), request.status(),
					trim(request.remark()), id);
		}
		else {
			jdbcTemplate.update(
					"""
							INSERT INTO scx_ocpx_channel_event_mapping
							(id,channel_id,upstream_event_id,conversion_event_id,media_event_id,status,remark,deleted,create_time,update_time)
							VALUES (?,?,?,?,?,?,?,0,NOW(3),NOW(3))
							""",
					mappingId, channelId, upstream.id(), conversion.id(), media.id(), request.status(),
					trim(request.remark()));
		}
		return mappingById(mappingId);
	}

	@Transactional
	public void disableMapping(String id) {
		MappingRow mapping = requireMapping(id);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, mapping.channelId(),
				OcpxAccessScope.PermissionLevel.MANAGE);
		if (draftMappingReferences(id) > 0) {
			throw new IllegalArgumentException("映射仍被监测链接草稿引用，请先移除绑定");
		}
		jdbcTemplate.update("""
				UPDATE scx_ocpx_channel_event_mapping SET status='DISABLED',update_time=NOW(3) WHERE id=?
				""", id);
	}

	public List<EventViews.Option> options(String channelId) {
		requireChannel(channelId);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		return jdbcTemplate.query("""
				SELECT m.id,u.event_code,u.event_name,v.event_code,v.event_name,d.event_code,d.event_name
				FROM scx_ocpx_channel_event_mapping m
				JOIN scx_ocpx_event_definition u ON u.id=m.upstream_event_id AND u.status='ACTIVE' AND u.deleted=0
				JOIN scx_ocpx_event_definition v ON v.id=m.conversion_event_id AND v.status='ACTIVE' AND v.deleted=0
				JOIN scx_ocpx_event_definition d ON d.id=m.media_event_id AND d.status='ACTIVE' AND d.deleted=0
				WHERE m.channel_id=? AND m.status='ACTIVE' AND m.deleted=0
				ORDER BY u.sort_order,u.event_code
				""",
				(rs, rowNum) -> new EventViews.Option(rs.getString(1), rs.getString(2), rs.getString(3),
						bindingName(rs.getString(2), rs.getString(3)), rs.getString(4), rs.getString(5),
						rs.getString(6), rs.getString(7)),
				channelId);
	}

	public List<EventViews.Binding> bindings(String monitorId) {
		requireMonitorScope(monitorId, OcpxAccessScope.PermissionLevel.VIEW);
		return jdbcTemplate.query(
				"""
						SELECT b.id,b.event_mapping_id,u.event_code,u.event_name,v.event_code,v.event_name,d.event_code,d.event_name,
						 b.assessment_unit_price,b.daily_limit
						FROM scx_ocpx_monitor_event_binding b
						JOIN scx_ocpx_channel_event_mapping m ON m.id=b.event_mapping_id
						JOIN scx_ocpx_event_definition u ON u.id=m.upstream_event_id
						JOIN scx_ocpx_event_definition v ON v.id=m.conversion_event_id
						JOIN scx_ocpx_event_definition d ON d.id=m.media_event_id
						WHERE b.monitor_id=? ORDER BY u.sort_order,u.event_code
						""",
				(rs, rowNum) -> new EventViews.Binding(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), bindingName(rs.getString(3), rs.getString(4)), rs.getString(5),
						rs.getString(6), rs.getString(7), rs.getString(8), rs.getBigDecimal(9), rs.getInt(10)),
				monitorId);
	}

	@Transactional
	public void replaceBindings(String monitorId, String channelId, List<EventRequests.MonitorBinding> bindings) {
		MonitorObject monitor = requireMonitorScope(monitorId, OcpxAccessScope.PermissionLevel.OPERATE);
		if (!monitor.channelId().equals(channelId)) {
			throw new IllegalArgumentException("监测链接与渠道不匹配");
		}
		List<EventRequests.MonitorBinding> safe = EventRequests.safeBindings(bindings);
		Set<String> mappingIds = new HashSet<>();
		for (EventRequests.MonitorBinding binding : safe) {
			if (!mappingIds.add(binding.eventMappingId())) {
				throw new IllegalArgumentException("同一上游事件不能重复绑定");
			}
			if (binding.assessmentUnitPrice().scale() > 6 || binding.assessmentUnitPrice().signum() < 0) {
				throw new IllegalArgumentException("考核单价格式无效");
			}
			MappingRow mapping = requireMapping(binding.eventMappingId());
			if (!channelId.equals(mapping.channelId()) || !"ACTIVE".equals(mapping.status())) {
				throw new IllegalArgumentException("事件映射未启用或不属于当前渠道");
			}
		}
		jdbcTemplate.update("DELETE FROM scx_ocpx_monitor_event_binding WHERE monitor_id=?", monitorId);
		for (EventRequests.MonitorBinding binding : safe) {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_monitor_event_binding
					(id,monitor_id,event_mapping_id,assessment_unit_price,daily_limit,create_time,update_time)
					VALUES (?,?,?,?,?,NOW(3),NOW(3))
					""", id(), monitorId, binding.eventMappingId(), binding.assessmentUnitPrice(),
					binding.dailyLimit());
		}
	}

	@Transactional
	public int publishSnapshot(String monitorId, String channelId, long routeVersion) {
		MonitorObject monitor = requireMonitorScope(monitorId, OcpxAccessScope.PermissionLevel.OPERATE);
		if (!monitor.channelId().equals(channelId)) {
			throw new IllegalArgumentException("监测链接与渠道不匹配");
		}
		List<EventViews.Binding> bindings = bindings(monitorId);
		for (EventViews.Binding binding : bindings) {
			MappingRow mapping = lockMapping(binding.eventMappingId());
			if (!channelId.equals(mapping.channelId()) || !"ACTIVE".equals(mapping.status())
					|| !mappingDefinitionsActiveForUpdate(mapping)) {
				throw new IllegalArgumentException("事件映射已停用或不属于当前渠道，请保存草稿后重试");
			}
		}
		for (EventViews.Binding binding : bindings) {
			jdbcTemplate.update("""
					INSERT INTO scx_ocpx_route_event_snapshot
					(id,monitor_id,route_version,event_mapping_id,upstream_event_code,upstream_event_name,
					 conversion_event_code,conversion_event_name,media_event_code,media_event_name,
					 assessment_unit_price,daily_limit,create_time)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))
					""", id(), monitorId, routeVersion, binding.eventMappingId(), binding.upstreamEventCode(),
					binding.upstreamEventName(), binding.conversionEventCode(), binding.conversionEventName(),
					binding.mediaEventCode(), binding.mediaEventName(), binding.assessmentUnitPrice(),
					binding.dailyLimit());
		}
		return bindings.size();
	}

	public Map<String, EventViews.BindingSummary> bindingSummaries(List<String> monitorIds) {
		if (monitorIds == null || monitorIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", java.util.Collections.nCopies(monitorIds.size(), "?"));
		Map<String, List<EventViews.Binding>> grouped = new LinkedHashMap<>();
		jdbcTemplate.query("""
				SELECT b.monitor_id,b.id,b.event_mapping_id,u.event_code,u.event_name,v.event_code,v.event_name,
				 d.event_code,d.event_name,b.assessment_unit_price,b.daily_limit
				FROM scx_ocpx_monitor_event_binding b
				JOIN scx_ocpx_channel_event_mapping m ON m.id=b.event_mapping_id
				JOIN scx_ocpx_event_definition u ON u.id=m.upstream_event_id
				JOIN scx_ocpx_event_definition v ON v.id=m.conversion_event_id
				JOIN scx_ocpx_event_definition d ON d.id=m.media_event_id
				WHERE b.monitor_id IN (""" + placeholders + ") ORDER BY u.sort_order,u.event_code", rs -> {
			String monitorId = rs.getString(1);
			grouped.computeIfAbsent(monitorId, ignored -> new ArrayList<>())
				.add(new EventViews.Binding(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
						bindingName(rs.getString(4), rs.getString(5)), rs.getString(6), rs.getString(7),
						rs.getString(8), rs.getString(9), rs.getBigDecimal(10), rs.getInt(11)));
		}, monitorIds.toArray());
		Map<String, EventViews.BindingSummary> result = new HashMap<>();
		grouped.forEach((monitorId, rows) -> result.put(monitorId, new EventViews.BindingSummary(rows.size(), rows
			.stream()
			.map(row -> row.bindingDisplayName() + " ¥" + row.assessmentUnitPrice().stripTrailingZeros().toPlainString()
					+ (row.dailyLimit() == 0 ? " / 不限量" : " / " + row.dailyLimit() + "次"))
			.collect(java.util.stream.Collectors.joining("；")))));
		return result;
	}

	@Transactional
	public void initializeChannel(String channelId) {
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.MANAGE);
		for (int i = 0; i < UPSTREAM_SEEDS.size(); i++) {
			upsertSeed("UPSTREAM", channelId, UPSTREAM_SEEDS.get(i), (i + 1) * 10);
		}
		for (int i = 0; i < MEDIA_SEEDS.size(); i++) {
			upsertSeed("MEDIA", channelId, MEDIA_SEEDS.get(i), (i + 1) * 10);
		}
		for (String code : DEFAULT_MAPPING_CODES) {
			String upstreamId = definitionId("UPSTREAM", channelId, code);
			String conversionId = definitionId("CONVERSION", "", code);
			String mediaId = definitionId("MEDIA", channelId, code);
			jdbcTemplate.update(
					"""
							INSERT INTO scx_ocpx_channel_event_mapping
							(id,channel_id,upstream_event_id,conversion_event_id,media_event_id,status,remark,deleted,create_time,update_time)
							VALUES (?,?,?,?,?,'ACTIVE','系统默认映射',0,NOW(3),NOW(3))
							ON DUPLICATE KEY UPDATE conversion_event_id=VALUES(conversion_event_id),media_event_id=VALUES(media_event_id),
							 update_time=NOW(3)
							""",
					id(), channelId, upstreamId, conversionId, mediaId);
		}
	}

	private void upsertSeed(String category, String channelId, Seed seed, int sort) {
		jdbcTemplate.update(
				"""
						INSERT INTO scx_ocpx_event_definition
						(id,category,channel_id,event_code,event_name,status,sort_order,built_in,deleted,create_time,update_time)
						VALUES (?,?,?,?,?,'ACTIVE',?,1,0,NOW(3),NOW(3))
						ON DUPLICATE KEY UPDATE event_name=VALUES(event_name),built_in=1,deleted=0,update_time=NOW(3)
						""",
				id(), category, channelId, seed.code(), seed.name(), sort);
	}

	private String definitionId(String category, String channelId, String code) {
		List<String> ids = jdbcTemplate.query("""
				SELECT id FROM scx_ocpx_event_definition WHERE category=? AND channel_id=? AND event_code=? AND deleted=0
				""", (rs, rowNum) -> rs.getString(1), category, channelId, code);
		if (ids.isEmpty()) {
			throw new IllegalStateException("default event definition is missing: " + category + "/" + code);
		}
		return ids.get(0);
	}

	private EventViews.Definition definitionById(String id) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT e.id,e.category,NULLIF(e.channel_id,''),c.channel_name,e.event_code,e.event_name,e.status,e.sort_order,
						 e.remark,e.built_in,
						 EXISTS(SELECT 1 FROM scx_ocpx_channel_event_mapping x WHERE x.deleted=0 AND
						   (x.upstream_event_id=e.id OR x.conversion_event_id=e.id OR x.media_event_id=e.id)),
						 (SELECT COUNT(*) FROM scx_ocpx_monitor_event_binding b
						   JOIN scx_ocpx_channel_event_mapping m ON m.id=b.event_mapping_id
						   WHERE m.upstream_event_id=e.id OR m.conversion_event_id=e.id OR m.media_event_id=e.id),
						 (SELECT COUNT(*) FROM scx_ocpx_route_event_snapshot s
						   WHERE (e.category='UPSTREAM' AND s.upstream_event_code=e.event_code AND s.monitor_id IN
						         (SELECT id FROM scx_ocpx_monitor_link WHERE channel_id=e.channel_id))
						      OR (e.category='CONVERSION' AND s.conversion_event_code=e.event_code)
						      OR (e.category='MEDIA' AND s.media_event_code=e.event_code AND s.monitor_id IN
						         (SELECT id FROM scx_ocpx_monitor_link WHERE channel_id=e.channel_id))),
						 e.create_time,e.update_time
						FROM scx_ocpx_event_definition e LEFT JOIN scx_ocpx_channel c ON c.id=e.channel_id WHERE e.id=? AND e.deleted=0
						""",
				this::definition, id);
	}

	private EventViews.Mapping mappingById(String id) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT m.id,m.channel_id,c.channel_name,u.id,u.event_code,u.event_name,v.id,v.event_code,v.event_name,
						 d.id,d.event_code,d.event_name,m.status,m.remark,
						 (SELECT COUNT(*) FROM scx_ocpx_monitor_event_binding b WHERE b.event_mapping_id=m.id),
						 (SELECT COUNT(*) FROM scx_ocpx_route_event_snapshot s WHERE s.event_mapping_id=m.id),m.create_time,m.update_time
						FROM scx_ocpx_channel_event_mapping m JOIN scx_ocpx_channel c ON c.id=m.channel_id
						JOIN scx_ocpx_event_definition u ON u.id=m.upstream_event_id
						JOIN scx_ocpx_event_definition v ON v.id=m.conversion_event_id
						JOIN scx_ocpx_event_definition d ON d.id=m.media_event_id WHERE m.id=? AND m.deleted=0
						""",
				this::mapping, id);
	}

	private EventViews.Definition definition(ResultSet rs, int rowNum) throws SQLException {
		return new EventViews.Definition(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8), rs.getString(9), rs.getBoolean(10),
				rs.getBoolean(11), rs.getLong(12), rs.getLong(13), rs.getTimestamp(14).toLocalDateTime(),
				rs.getTimestamp(15).toLocalDateTime());
	}

	private EventViews.Mapping mapping(ResultSet rs, int rowNum) throws SQLException {
		return new EventViews.Mapping(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
				rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getLong(15), rs.getLong(16),
				rs.getTimestamp(17).toLocalDateTime(), rs.getTimestamp(18).toLocalDateTime());
	}

	private DefinitionRow requireDefinition(String id) {
		List<DefinitionRow> rows = jdbcTemplate.query("""
				SELECT id,category,channel_id,event_code,status FROM scx_ocpx_event_definition WHERE id=? AND deleted=0
				""", (rs, rowNum) -> new DefinitionRow(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getString(4), rs.getString(5)), id);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("事件不存在");
		}
		return rows.get(0);
	}

	private MappingRow requireMapping(String id) {
		List<MappingRow> rows = jdbcTemplate.query("""
				SELECT id,channel_id,upstream_event_id,conversion_event_id,media_event_id,status
				FROM scx_ocpx_channel_event_mapping WHERE id=? AND deleted=0
				""", (rs, rowNum) -> new MappingRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6)), id);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("事件映射不存在");
		}
		return rows.get(0);
	}

	private MappingRow lockMapping(String id) {
		List<MappingRow> rows = jdbcTemplate.query("""
				SELECT id,channel_id,upstream_event_id,conversion_event_id,media_event_id,status
				FROM scx_ocpx_channel_event_mapping WHERE id=? AND deleted=0 FOR UPDATE
				""", (rs, rowNum) -> new MappingRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6)), id);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("事件映射不存在");
		}
		return rows.get(0);
	}

	private boolean mappingDefinitionsActiveForUpdate(MappingRow mapping) {
		List<String> statuses = jdbcTemplate.query("""
				SELECT status FROM scx_ocpx_event_definition
				WHERE id IN (?,?,?) AND deleted=0 FOR UPDATE
				""", (rs, rowNum) -> rs.getString(1), mapping.upstreamEventId(), mapping.conversionEventId(),
				mapping.mediaEventId());
		return statuses.size() == 3 && statuses.stream().allMatch("ACTIVE"::equals);
	}

	private String requireChannel(String channelId) {
		if (!StringUtils.hasText(channelId)) {
			throw new IllegalArgumentException("上游和媒体事件必须选择渠道");
		}
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_channel WHERE id=? AND deleted=0",
				Long.class, channelId);
		if (count == null || count == 0) {
			throw new IllegalArgumentException("渠道不存在");
		}
		return channelId;
	}

	private void requireDefinitionScope(String category, String channelId, OcpxAccessScope.PermissionLevel level) {
		if ("CONVERSION".equals(category)) {
			accessScope.requireAll(level);
			return;
		}
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, level);
	}

	private void appendChannelDefinitionScope(StringBuilder where, List<Object> args,
			OcpxAccessScope.PermissionLevel level) {
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.CHANNEL, level);
		if (restriction.unrestricted()) {
			return;
		}
		if (restriction.ids().isEmpty()) {
			where.append(" AND 1=0");
			return;
		}
		where.append(" AND (e.category='CONVERSION' OR e.channel_id IN (")
			.append(String.join(",", java.util.Collections.nCopies(restriction.ids().size(), "?")))
			.append("))");
		args.addAll(restriction.ids());
	}

	private void appendChannelScope(StringBuilder where, List<Object> args, String column,
			OcpxAccessScope.PermissionLevel level) {
		OcpxAccessScope.Restriction restriction = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.CHANNEL, level);
		if (restriction.unrestricted()) {
			return;
		}
		if (restriction.ids().isEmpty()) {
			where.append(" AND 1=0");
			return;
		}
		where.append(" AND ")
			.append(column)
			.append(" IN (")
			.append(String.join(",", java.util.Collections.nCopies(restriction.ids().size(), "?")))
			.append(")");
		args.addAll(restriction.ids());
	}

	private MonitorObject requireMonitorScope(String monitorId, OcpxAccessScope.PermissionLevel level) {
		List<MonitorObject> rows = jdbcTemplate.query(
				"SELECT advertiser_id,channel_id FROM scx_ocpx_monitor_link WHERE id=? AND deleted=0",
				(rs, rowNum) -> new MonitorObject(rs.getString(1), rs.getString(2)), monitorId);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("监测链接不存在或无权访问");
		}
		MonitorObject monitor = rows.get(0);
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, monitor.advertiserId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, monitor.channelId(), level);
		return monitor;
	}

	private void ensureUniqueDefinition(String id, String category, String channelId, String code) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_event_definition
				WHERE category=? AND channel_id=? AND event_code=? AND deleted=0 AND (? IS NULL OR id<>?)
				""", Long.class, category, channelId, code, id, id);
		if (count != null && count > 0) {
			throw new IllegalArgumentException("当前范围内事件码已存在");
		}
	}

	private long referenceCount(String definitionId) {
		Long value = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_channel_event_mapping
				WHERE deleted=0 AND (upstream_event_id=? OR conversion_event_id=? OR media_event_id=?)
				""", Long.class, definitionId, definitionId, definitionId);
		return value == null ? 0 : value;
	}

	private long draftReferenceCount(String definitionId) {
		Long value = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM scx_ocpx_monitor_event_binding b
				JOIN scx_ocpx_channel_event_mapping m ON m.id=b.event_mapping_id
				WHERE m.upstream_event_id=? OR m.conversion_event_id=? OR m.media_event_id=?
				""", Long.class, definitionId, definitionId, definitionId);
		return value == null ? 0 : value;
	}

	private long draftMappingReferences(String mappingId) {
		Long value = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM scx_ocpx_monitor_event_binding WHERE event_mapping_id=?", Long.class, mappingId);
		return value == null ? 0 : value;
	}

	private static void append(StringBuilder sql, List<Object> args, String clause, String value) {
		if (StringUtils.hasText(value)) {
			sql.append(clause);
			args.add(value.trim());
		}
	}

	private static String bindingName(String code, String name) {
		return "NEXT_DAY_RETENTION".equals(code) ? "次日回访" : name;
	}

	private static String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private static String id() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private record Seed(String code, String name) {
	}

	private record DefinitionRow(String id, String category, String channelId, String eventCode, String status) {
	}

	private record MappingRow(String id, String channelId, String upstreamEventId, String conversionEventId,
			String mediaEventId, String status) {
	}

	private record MonitorObject(String advertiserId, String channelId) {
	}

}
