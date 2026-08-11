package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.admin.dto.EventRequests;
import com.yzytech.scx.ocpx.admin.dto.EventViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannelEventMapping;
import com.yzytech.scx.ocpx.admin.entity.OcpxEventChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxEventDefinition;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelEventMappingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventDefinitionMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcpxEventModelService {

	private final OcpxEventDefinitionMapper definitionMapper;

	private final OcpxChannelEventMappingMapper mappingMapper;

	private final OcpxChannelMapper channelMapper;

	private final OcpxEventChangeAuditMapper auditMapper;

	private final OcpxAccessScope accessScope;

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	public IPage<EventViews.DefinitionDetail> definitions(long current, long size, String status, String direction,
			String keyword) {
		Page<OcpxEventDefinition> page = definitionMapper.selectPage(new Page<>(current, Math.min(size, 100)),
				Wrappers.<OcpxEventDefinition>lambdaQuery()
					.eq(OcpxEventDefinition::getMappingState, "CANONICAL")
					.eq(StringUtils.hasText(status), OcpxEventDefinition::getStatus, status)
					.eq(StringUtils.hasText(direction), OcpxEventDefinition::getDirection, direction)
					.and(StringUtils.hasText(keyword),
							query -> query.like(OcpxEventDefinition::getEventCode, keyword.trim())
								.or()
								.like(OcpxEventDefinition::getEventName, keyword.trim()))
					.orderByAsc(OcpxEventDefinition::getSortOrder, OcpxEventDefinition::getEventCode));
		return page.convert(this::definitionView);
	}

	public EventViews.DefinitionDetail definition(String id) {
		return definitionView(requireDefinition(id));
	}

	@Transactional
	public EventViews.DefinitionDetail createDefinition(String idempotencyKey, EventRequests.CreateDefinition request) {
		accessScope.requireAll(OcpxAccessScope.PermissionLevel.MANAGE);
		String requestKey = requestKey(idempotencyKey);
		String hash = sha256(String.join("|", request.code(), request.name(), request.direction(),
				request.standardType(), request.billingEligible().toString(), request.subjectType(), request.status(),
				request.sortOrder().toString(), clean(request.description())));
		OcpxEventDefinition existing = definitionMapper.selectOne(
				Wrappers.<OcpxEventDefinition>lambdaQuery().eq(OcpxEventDefinition::getCreateRequestKey, requestKey));
		if (existing != null) {
			if (!hash.equals(existing.getCreateRequestHash()) || !"CANONICAL".equals(existing.getMappingState())) {
				throw conflict("OCPX_EVENT_IDEMPOTENCY_CONFLICT：幂等键已用于不同事件定义请求");
			}
			return definitionView(existing);
		}
		OcpxEventDefinition row = new OcpxEventDefinition();
		row.setCategory("STANDARD");
		row.setChannelId("");
		row.setEventCode(request.code());
		row.setEventName(request.name().trim());
		row.setDirection(request.direction());
		row.setStandardType(request.standardType());
		row.setBillingEligible(request.billingEligible());
		row.setSubjectType(request.subjectType());
		row.setStatus(request.status());
		row.setSortOrder(request.sortOrder());
		row.setRemark(nullable(request.description()));
		row.setBuiltIn(false);
		row.setConfigVersion(1L);
		row.setMappingState("CANONICAL");
		row.setCreateRequestKey(requestKey);
		row.setCreateRequestHash(hash);
		row.setCreateBy(actor());
		row.setUpdateBy(actor());
		row.setDeleted(false);
		try {
			definitionMapper.insert(row);
		}
		catch (DuplicateKeyException duplicate) {
			throw conflict("OCPX_EVENT_CODE_DUPLICATE：标准事件 code 已存在");
		}
		audit("EVENT_DEFINITION", row.getId(), null, null, "CREATE", 0, 1, "创建标准事件定义；未发布、未产生 Callback 或计费副作用");
		return definitionView(row);
	}

	@Transactional
	public EventViews.DefinitionDetail updateDefinition(String id, EventRequests.UpdateDefinition request) {
		accessScope.requireAll(OcpxAccessScope.PermissionLevel.MANAGE);
		OcpxEventDefinition row = lockDefinition(id);
		requireVersion(row.getConfigVersion(), request.expectedVersion(), "OCPX_EVENT_VERSION_CONFLICT");
		long before = version(row.getConfigVersion());
		row.setEventName(request.name().trim());
		row.setDirection(request.direction());
		row.setStandardType(request.standardType());
		row.setBillingEligible(request.billingEligible());
		row.setSubjectType(request.subjectType());
		row.setSortOrder(request.sortOrder());
		row.setRemark(nullable(request.description()));
		row.setConfigVersion(before + 1);
		row.setUpdateBy(actor());
		definitionMapper.updateById(row);
		audit("EVENT_DEFINITION", row.getId(), null, null, "UPDATE", before, before + 1, "更新定义元数据；code 保持不可变，已发布快照未变化");
		return definitionView(row);
	}

	@Transactional
	public EventViews.StatusResult changeDefinitionStatus(String id, EventRequests.StatusAction request) {
		accessScope.requireAll(OcpxAccessScope.PermissionLevel.MANAGE);
		OcpxEventDefinition row = lockDefinition(id);
		requireVersion(row.getConfigVersion(), request.expectedVersion(), "OCPX_EVENT_VERSION_CONFLICT");
		EventViews.ReferenceSummary references = definitionReferences(id);
		if (!"ACTIVE".equals(request.status()) && references.statusChangeRequiresConfirmation()
				&& !Boolean.TRUE.equals(request.confirmed())) {
			throw conflict("OCPX_EVENT_REFERENCE_CONFIRM_REQUIRED：存在引用，请确认影响后再停用或归档");
		}
		long before = version(row.getConfigVersion());
		row.setStatus(request.status());
		row.setArchivedAt("ARCHIVED".equals(request.status()) ? java.time.LocalDateTime.now() : null);
		row.setConfigVersion(before + 1);
		row.setUpdateBy(actor());
		definitionMapper.updateById(row);
		audit("EVENT_DEFINITION", id, null, null, "STATUS_" + request.status(), before, before + 1,
				"状态变更仅影响新绑定和后续发布；旧快照保持不变；原因=" + safeReason(request.reason()));
		return new EventViews.StatusResult(id, row.getStatus(), version(row.getConfigVersion()), references,
				"仅阻止新绑定/新发布，不修改旧快照");
	}

	public IPage<EventViews.MappingDetail> mappings(long current, long size, String channelId, String status,
			String direction, String keyword) {
		OcpxAccessScope.Restriction channels = accessScope.current()
			.restriction(OcpxAccessScope.ScopeType.CHANNEL, OcpxAccessScope.PermissionLevel.VIEW);
		if (StringUtils.hasText(channelId)) {
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		}
		if (!channels.unrestricted() && channels.ids().isEmpty()) {
			return emptyPage(current, size);
		}
		Page<OcpxChannelEventMapping> page = mappingMapper.selectPage(new Page<>(current, Math.min(size, 100)),
				Wrappers.<OcpxChannelEventMapping>lambdaQuery()
					.eq(OcpxChannelEventMapping::getMappingState, "CANONICAL")
					.in(!channels.unrestricted(), OcpxChannelEventMapping::getChannelId, channels.ids())
					.eq(StringUtils.hasText(channelId), OcpxChannelEventMapping::getChannelId, channelId)
					.eq(StringUtils.hasText(status), OcpxChannelEventMapping::getStatus, status)
					.eq(StringUtils.hasText(direction), OcpxChannelEventMapping::getDirection, direction)
					.and(StringUtils.hasText(keyword),
							query -> query.like(OcpxChannelEventMapping::getExternalCode, keyword.trim())
								.or()
								.like(OcpxChannelEventMapping::getAdapterCode, keyword.trim()))
					.orderByAsc(OcpxChannelEventMapping::getChannelId, OcpxChannelEventMapping::getExternalCode));
		return page.convert(this::mappingView);
	}

	public EventViews.MappingDetail mapping(String id) {
		OcpxChannelEventMapping row = requireMapping(id);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, row.getChannelId(), OcpxAccessScope.PermissionLevel.VIEW);
		return mappingView(row);
	}

	@Transactional
	public EventViews.MappingDetail createMapping(String idempotencyKey, EventRequests.CreateMapping request) {
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, request.channelId(),
				OcpxAccessScope.PermissionLevel.MANAGE);
		String requestKey = requestKey(idempotencyKey);
		String hash = sha256(String.join("|", request.channelId(), request.adapterCode(), request.externalCode(),
				request.direction(), request.eventDefinitionId(), request.parameterTemplateCode(),
				request.successRuleCode(), request.status(), clean(request.description())));
		OcpxChannelEventMapping existing = mappingMapper.selectOne(Wrappers.<OcpxChannelEventMapping>lambdaQuery()
			.eq(OcpxChannelEventMapping::getCreateRequestKey, requestKey));
		if (existing != null) {
			accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, existing.getChannelId(),
					OcpxAccessScope.PermissionLevel.MANAGE);
			if (!hash.equals(existing.getCreateRequestHash()) || !"CANONICAL".equals(existing.getMappingState())) {
				throw conflict("OCPX_EVENT_MAPPING_IDEMPOTENCY_CONFLICT：幂等键已用于不同映射请求");
			}
			return mappingView(existing);
		}
		MappingContext context = validateMapping(request.channelId(), request.adapterCode(), request.direction(),
				request.eventDefinitionId(), request.parameterTemplateCode());
		OcpxChannelEventMapping row = new OcpxChannelEventMapping();
		row.setChannelId(context.channel().getId());
		row.setAdapterCode(request.adapterCode());
		row.setExternalCode(request.externalCode());
		row.setDirection(request.direction());
		row.setEventDefinitionId(context.definition().getId());
		row.setParameterTemplateCode(request.parameterTemplateCode());
		row.setSuccessRuleCode(request.successRuleCode());
		row.setStatus(request.status());
		row.setConfigVersion(1L);
		row.setMappingState("CANONICAL");
		row.setCreateRequestKey(requestKey);
		row.setCreateRequestHash(hash);
		row.setRemark(nullable(request.description()));
		row.setCreateBy(actor());
		row.setUpdateBy(actor());
		row.setDeleted(false);
		try {
			mappingMapper.insert(row);
		}
		catch (DuplicateKeyException duplicate) {
			throw conflict("OCPX_EVENT_MAPPING_DUPLICATE：同一渠道、Adapter、方向和 externalCode 已存在");
		}
		audit("CHANNEL_EVENT_MAPPING", row.getId(), row.getChannelId(), null, "CREATE", 0, 1, "创建受控渠道事件映射；未执行第三方调用");
		return mappingView(row);
	}

	@Transactional
	public EventViews.MappingDetail updateMapping(String id, EventRequests.UpdateMapping request) {
		OcpxChannelEventMapping row = lockMapping(id);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, row.getChannelId(),
				OcpxAccessScope.PermissionLevel.MANAGE);
		requireVersion(row.getConfigVersion(), request.expectedVersion(), "OCPX_EVENT_MAPPING_VERSION_CONFLICT");
		if (!row.getExternalCode().equals(request.externalCode())
				&& mappingReferences(id).publishedSnapshotCount() > 0) {
			throw conflict("OCPX_EVENT_MAPPING_CODE_LOCKED：存在已发布快照时 externalCode 不可修改");
		}
		validateMapping(row.getChannelId(), row.getAdapterCode(), request.direction(), request.eventDefinitionId(),
				request.parameterTemplateCode());
		long before = version(row.getConfigVersion());
		row.setExternalCode(request.externalCode());
		row.setDirection(request.direction());
		row.setEventDefinitionId(request.eventDefinitionId());
		row.setParameterTemplateCode(request.parameterTemplateCode());
		row.setSuccessRuleCode(request.successRuleCode());
		row.setRemark(nullable(request.description()));
		row.setConfigVersion(before + 1);
		row.setUpdateBy(actor());
		try {
			mappingMapper.updateById(row);
		}
		catch (DuplicateKeyException duplicate) {
			throw conflict("OCPX_EVENT_MAPPING_DUPLICATE：同一渠道、Adapter、方向和 externalCode 已存在");
		}
		audit("CHANNEL_EVENT_MAPPING", row.getId(), row.getChannelId(), null, "UPDATE", before, before + 1,
				"更新受控映射草稿；旧快照保持不变");
		return mappingView(row);
	}

	@Transactional
	public EventViews.StatusResult changeMappingStatus(String id, EventRequests.StatusAction request) {
		OcpxChannelEventMapping row = lockMapping(id);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, row.getChannelId(),
				OcpxAccessScope.PermissionLevel.MANAGE);
		requireVersion(row.getConfigVersion(), request.expectedVersion(), "OCPX_EVENT_MAPPING_VERSION_CONFLICT");
		EventViews.ReferenceSummary references = mappingReferences(id);
		if (!"ACTIVE".equals(request.status()) && references.statusChangeRequiresConfirmation()
				&& !Boolean.TRUE.equals(request.confirmed())) {
			throw conflict("OCPX_EVENT_MAPPING_REFERENCE_CONFIRM_REQUIRED：存在引用，请确认影响后再停用或归档");
		}
		long before = version(row.getConfigVersion());
		row.setStatus(request.status());
		row.setArchivedAt("ARCHIVED".equals(request.status()) ? java.time.LocalDateTime.now() : null);
		row.setConfigVersion(before + 1);
		row.setUpdateBy(actor());
		mappingMapper.updateById(row);
		audit("CHANNEL_EVENT_MAPPING", id, row.getChannelId(), null, "STATUS_" + request.status(), before, before + 1,
				"状态变更仅影响新绑定和后续发布；旧快照不变；原因=" + safeReason(request.reason()));
		return new EventViews.StatusResult(id, row.getStatus(), version(row.getConfigVersion()), references,
				"仅阻止新绑定/新发布，不修改旧快照");
	}

	public EventViews.ReferenceSummary definitionReference(String id) {
		requireDefinition(id);
		return definitionReferences(id);
	}

	public EventViews.ReferenceSummary mappingReference(String id) {
		OcpxChannelEventMapping row = requireMapping(id);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, row.getChannelId(), OcpxAccessScope.PermissionLevel.VIEW);
		return mappingReferences(id);
	}

	private MappingContext validateMapping(String channelId, String adapterCode, String direction, String definitionId,
			String templateCode) {
		OcpxChannel channel = channelMapper.selectById(channelId);
		if (channel == null || !"ACTIVE".equals(channel.getStatus()) || !adapterCode.equals(channel.getAdapterCode())) {
			throw new IllegalArgumentException("渠道未启用或 Adapter 与渠道协议目录不一致");
		}
		OcpxEventDefinition definition = requireDefinition(definitionId);
		if (!"ACTIVE".equals(definition.getStatus()) || !(direction.equals(definition.getDirection())
				|| "BIDIRECTIONAL".equals(definition.getDirection()))) {
			throw new IllegalArgumentException("标准事件未启用或方向与渠道映射不一致");
		}
		List<AdapterRow> adapters = jdbcTemplate.query("""
				SELECT capability_revision,capabilities_json FROM scx_ocpx_adapter
				WHERE adapter_code=? AND status='ACTIVE'
				""", (rs, index) -> new AdapterRow(rs.getLong(1), rs.getString(2)), adapterCode);
		if (adapters.isEmpty() || !supports(adapters.get(0).capabilitiesJson(), "eventMappingDirections", direction)
				|| !supports(adapters.get(0).capabilitiesJson(), "eventParameterTemplates", templateCode)) {
			throw new IllegalArgumentException("Adapter 未声明该事件方向或受控参数模板能力");
		}
		return new MappingContext(channel, definition, adapters.get(0).revision());
	}

	private boolean supports(String json, String field, String value) {
		try {
			JsonNode values = objectMapper.readTree(json).path(field);
			if (!values.isArray()) {
				return false;
			}
			return java.util.stream.StreamSupport.stream(values.spliterator(), false)
				.anyMatch(item -> item.isTextual() && value.equals(item.asText()));
		}
		catch (Exception error) {
			throw new IllegalArgumentException("Adapter 事件能力配置无效");
		}
	}

	private EventViews.DefinitionDetail definitionView(OcpxEventDefinition row) {
		return new EventViews.DefinitionDetail(row.getId(), row.getEventCode(), row.getEventName(), row.getDirection(),
				row.getStandardType(), Boolean.TRUE.equals(row.getBillingEligible()), row.getSubjectType(),
				row.getStatus(), row.getSortOrder() == null ? 0 : row.getSortOrder(), row.getRemark(),
				version(row.getConfigVersion()), row.getMappingState(), definitionReferences(row.getId()),
				row.getCreateTime(), row.getUpdateTime());
	}

	private EventViews.MappingDetail mappingView(OcpxChannelEventMapping row) {
		OcpxChannel channel = channelMapper.selectById(row.getChannelId());
		OcpxEventDefinition definition = definitionMapper.selectById(row.getEventDefinitionId());
		long capabilityRevision = jdbcTemplate.queryForObject(
				"SELECT capability_revision FROM scx_ocpx_adapter WHERE adapter_code=?", Long.class,
				row.getAdapterCode());
		return new EventViews.MappingDetail(row.getId(), row.getChannelId(),
				channel == null ? "-" : channel.getChannelName(), row.getAdapterCode(), capabilityRevision,
				row.getExternalCode(), row.getDirection(), row.getEventDefinitionId(),
				definition == null ? "-" : definition.getEventCode(),
				definition == null ? "-" : definition.getEventName(), row.getParameterTemplateCode(),
				row.getSuccessRuleCode(), row.getStatus(), row.getRemark(), version(row.getConfigVersion()),
				row.getMappingState(), mappingReferences(row.getId()), row.getCreateTime(), row.getUpdateTime());
	}

	private EventViews.ReferenceSummary definitionReferences(String id) {
		long product = count(
				"SELECT COUNT(*) FROM scx_ocpx_product_event_policy WHERE event_definition_id=? AND status='ACTIVE'",
				id);
		long tasks = count(
				"SELECT COUNT(*) FROM scx_ocpx_task_event_binding WHERE event_definition_id=? AND status='ACTIVE'", id);
		long snapshots = count("SELECT COUNT(*) FROM scx_ocpx_route_event_snapshot WHERE event_definition_id=?", id);
		return new EventViews.ReferenceSummary(product, tasks, snapshots, snapshots > 0,
				product + tasks + snapshots > 0);
	}

	private EventViews.ReferenceSummary mappingReferences(String id) {
		long tasks = count(
				"SELECT COUNT(*) FROM scx_ocpx_task_event_binding WHERE channel_event_mapping_id=? AND status='ACTIVE'",
				id);
		long snapshots = count("SELECT COUNT(*) FROM scx_ocpx_route_event_snapshot WHERE event_mapping_id=?", id);
		return new EventViews.ReferenceSummary(0, tasks, snapshots, snapshots > 0, tasks + snapshots > 0);
	}

	private long count(String sql, String id) {
		Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
		return count == null ? 0 : count;
	}

	private OcpxEventDefinition requireDefinition(String id) {
		OcpxEventDefinition row = definitionMapper.selectById(id);
		if (row == null || !"CANONICAL".equals(row.getMappingState())) {
			throw new IllegalArgumentException("事件定义不存在或无权访问");
		}
		return row;
	}

	private OcpxEventDefinition lockDefinition(String id) {
		OcpxEventDefinition row = definitionMapper.selectOne(Wrappers.<OcpxEventDefinition>lambdaQuery()
			.eq(OcpxEventDefinition::getId, id)
			.eq(OcpxEventDefinition::getMappingState, "CANONICAL")
			.last("FOR UPDATE"));
		if (row == null) {
			throw new IllegalArgumentException("事件定义不存在或无权访问");
		}
		return row;
	}

	private OcpxChannelEventMapping requireMapping(String id) {
		OcpxChannelEventMapping row = mappingMapper.selectById(id);
		if (row == null || !"CANONICAL".equals(row.getMappingState())) {
			throw new IllegalArgumentException("渠道事件映射不存在或无权访问");
		}
		return row;
	}

	private OcpxChannelEventMapping lockMapping(String id) {
		OcpxChannelEventMapping row = mappingMapper.selectOne(Wrappers.<OcpxChannelEventMapping>lambdaQuery()
			.eq(OcpxChannelEventMapping::getId, id)
			.eq(OcpxChannelEventMapping::getMappingState, "CANONICAL")
			.last("FOR UPDATE"));
		if (row == null) {
			throw new IllegalArgumentException("渠道事件映射不存在或无权访问");
		}
		return row;
	}

	private void audit(String objectType, String objectId, String channelId, String taskId, String action, long before,
			long after, String summary) {
		OcpxEventChangeAudit row = new OcpxEventChangeAudit();
		row.setObjectType(objectType);
		row.setObjectId(objectId);
		row.setChannelId(channelId);
		row.setTaskId(taskId);
		row.setAction(action);
		row.setBeforeVersion(before);
		row.setAfterVersion(after);
		row.setActor(actor());
		row.setChangeSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
		auditMapper.insert(row);
	}

	private void requireVersion(Long actual, long expected, String code) {
		if (version(actual) != expected) {
			throw conflict(code + "：配置已被其他操作修改，请刷新后重试");
		}
	}

	private String requestKey(String value) {
		if (!StringUtils.hasText(value) || !value.matches("^[A-Za-z0-9_-]{16,64}$")) {
			throw new IllegalArgumentException("Idempotency-Key 必须为 16 到 64 位字母、数字、下划线或连字符");
		}
		return value;
	}

	private String safeReason(String value) {
		String reason = StringUtils.hasText(value) ? value.trim() : "未填写原因";
		reason = reason.replaceAll("[\\r\\n\\t]+", " ")
			.replaceAll("(?i)(https?://|secret|password|token|api[-_]?key|credential)[^\\s]*", "[REDACTED]")
			.replaceAll("[A-Za-z0-9+/=_-]{32,}", "[REDACTED]");
		return reason.length() > 255 ? reason.substring(0, 255) : reason;
	}

	private String actor() {
		String value = SecurityUtils.getUserName();
		return StringUtils.hasText(value) ? value : "unknown";
	}

	private String nullable(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private String clean(String value) {
		return nullable(value) == null ? "" : nullable(value);
	}

	private long version(Long value) {
		return value == null ? 0 : value;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception error) {
			throw new IllegalStateException("无法计算事件配置请求摘要", error);
		}
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	private IPage<EventViews.MappingDetail> emptyPage(long current, long size) {
		Page<EventViews.MappingDetail> page = new Page<>(current, Math.min(size, 100), 0);
		page.setRecords(List.of());
		return page;
	}

	private record AdapterRow(long revision, String capabilitiesJson) {
	}

	private record MappingContext(OcpxChannel channel, OcpxEventDefinition definition, long adapterRevision) {
	}

}
