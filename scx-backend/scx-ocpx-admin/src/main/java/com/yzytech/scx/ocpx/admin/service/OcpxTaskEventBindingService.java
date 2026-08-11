package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.ocpx.admin.dto.EventRequests;
import com.yzytech.scx.ocpx.admin.dto.EventViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannel;
import com.yzytech.scx.ocpx.admin.entity.OcpxChannelEventMapping;
import com.yzytech.scx.ocpx.admin.entity.OcpxEventChangeAudit;
import com.yzytech.scx.ocpx.admin.entity.OcpxEventDefinition;
import com.yzytech.scx.ocpx.admin.entity.OcpxMediaAccount;
import com.yzytech.scx.ocpx.admin.entity.OcpxProductEventPolicy;
import com.yzytech.scx.ocpx.admin.entity.OcpxProduct;
import com.yzytech.scx.ocpx.admin.entity.OcpxTask;
import com.yzytech.scx.ocpx.admin.entity.OcpxTaskEventBinding;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelEventMappingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxChannelMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventChangeAuditMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxEventDefinitionMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaAccountMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductEventPolicyMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxProductMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskEventBindingMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxTaskMapper;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcpxTaskEventBindingService {

	public static final String LIMIT_WINDOW = "NATURAL_DAY";

	public static final String LIMIT_TIMEZONE = "Asia/Shanghai";

	public static final String SAMPLING_ALGORITHM = "SHA256_MOD_10000_V1";

	private final OcpxTaskMapper taskMapper;

	private final OcpxTaskEventBindingMapper bindingMapper;

	private final OcpxEventDefinitionMapper definitionMapper;

	private final OcpxChannelEventMappingMapper mappingMapper;

	private final OcpxProductEventPolicyMapper productPolicyMapper;

	private final OcpxProductMapper productMapper;

	private final OcpxMediaAccountMapper mediaAccountMapper;

	private final OcpxChannelMapper channelMapper;

	private final OcpxEventChangeAuditMapper auditMapper;

	private final OcpxAccessScope accessScope;

	private final OcpxConfigChangeService configChangeService;

	public EventViews.TaskBindingsResult bindings(String taskId) {
		OcpxTask task = requireTask(taskId, OcpxAccessScope.PermissionLevel.VIEW, false);
		return result(task, 0);
	}

	public EventViews.TaskBindingOptions options(String taskId) {
		OcpxTask task = requireTask(taskId, OcpxAccessScope.PermissionLevel.VIEW, false);
		return options(task.getChannelId(), task.getProductId(), task.getMediaAccountId());
	}

	public EventViews.TaskBindingOptions options(String channelId, String productId, String mediaAccountId) {
		if (!StringUtils.hasText(channelId) || !StringUtils.hasText(productId)
				|| !StringUtils.hasText(mediaAccountId)) {
			throw new IllegalArgumentException("产品、媒体账号和渠道必须完整选择");
		}
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, channelId, OcpxAccessScope.PermissionLevel.VIEW);
		accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, mediaAccountId,
				OcpxAccessScope.PermissionLevel.VIEW);
		OcpxProduct product = productMapper.selectById(productId);
		OcpxMediaAccount mediaAccount = mediaAccountMapper.selectById(mediaAccountId);
		if (product == null || mediaAccount == null || !"ACTIVE".equals(product.getStatus())
				|| !"ACTIVE".equals(mediaAccount.getStatus()) || !channelId.equals(mediaAccount.getChannelId())) {
			throw new IllegalArgumentException("产品、媒体账号或渠道不存在、未启用或关系不一致");
		}
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, product.getAdvertiserId(),
				OcpxAccessScope.PermissionLevel.VIEW);
		OcpxChannel channel = channelMapper.selectById(channelId);
		if (channel == null || !"ACTIVE".equals(channel.getStatus())) {
			throw new IllegalArgumentException("渠道不存在、未启用或无权访问");
		}
		List<OcpxChannelEventMapping> mappings = mappingMapper.selectList(Wrappers.<OcpxChannelEventMapping>lambdaQuery()
			.eq(OcpxChannelEventMapping::getChannelId, channelId)
			.eq(OcpxChannelEventMapping::getAdapterCode, channel.getAdapterCode())
			.eq(OcpxChannelEventMapping::getDirection, "DOWNSTREAM_DELIVERY")
			.eq(OcpxChannelEventMapping::getStatus, "ACTIVE")
			.eq(OcpxChannelEventMapping::getMappingState, "CANONICAL")
			.orderByAsc(OcpxChannelEventMapping::getExternalCode));
		Set<String> definitionIds = mappings.stream()
			.map(OcpxChannelEventMapping::getEventDefinitionId)
			.collect(java.util.stream.Collectors.toSet());
		List<OcpxEventDefinition> definitions = definitionIds.isEmpty() ? List.of()
				: definitionMapper.selectList(Wrappers.<OcpxEventDefinition>lambdaQuery()
					.in(OcpxEventDefinition::getId, definitionIds)
					.eq(OcpxEventDefinition::getStatus, "ACTIVE")
					.eq(OcpxEventDefinition::getMappingState, "CANONICAL")
					.orderByAsc(OcpxEventDefinition::getSortOrder, OcpxEventDefinition::getEventCode));
		Map<String, OcpxEventDefinition> definitionsById = new HashMap<>();
		definitions.forEach(row -> definitionsById.put(row.getId(), row));
		List<EventViews.DefinitionOption> definitionOptions = definitions.stream()
			.map(row -> new EventViews.DefinitionOption(row.getId(), row.getEventCode(), row.getEventName(),
					row.getDirection(), row.getStandardType(), Boolean.TRUE.equals(row.getBillingEligible()),
					version(row.getConfigVersion())))
			.toList();
		List<EventViews.MappingOption> mappingOptions = mappings.stream()
			.filter(row -> definitionsById.containsKey(row.getEventDefinitionId()))
			.map(row -> new EventViews.MappingOption(row.getId(), row.getChannelId(), row.getAdapterCode(),
					row.getExternalCode(), row.getDirection(), row.getEventDefinitionId(),
					row.getParameterTemplateCode(), version(row.getConfigVersion())))
			.toList();
		return new EventViews.TaskBindingOptions(definitionOptions, mappingOptions,
				productTemplates(productId, mappings, definitionsById), channelId, channel.getAdapterCode(),
				LIMIT_WINDOW, LIMIT_TIMEZONE, SAMPLING_ALGORITHM);
	}

	@Transactional
	public EventViews.TaskBindingsResult replace(String taskId, EventRequests.ReplaceTaskBindings request) {
		OcpxTask task = requireTask(taskId, OcpxAccessScope.PermissionLevel.OPERATE, true);
		if (version(task.getConfigVersion()) != request.expectedTaskVersion()) {
			throw conflict("OCPX_TASK_VERSION_CONFLICT：任务已被其他操作修改，请刷新后重试");
		}
		if ("ARCHIVED".equals(task.getStatus())) {
			throw conflict("OCPX_TASK_ARCHIVED：归档任务不能修改事件绑定");
		}
		long before = version(task.getConfigVersion());
		sync(task, EventRequests.safeTaskBindings(request.bindings()), false);
		task.setStatus("DRAFT");
		task.setConfigVersion(before + 1);
		task.setRepublishRequired(true);
		task.setRepublishReason("TASK_EVENT_BINDINGS_CHANGED");
		task.setUpdateBy(actor());
		taskMapper.updateById(task);
		int affected = configChangeService.taskChanged(taskId, "任务事件绑定已更新，需显式重发");
		audit("TASK_EVENT_BINDING", taskId, task.getChannelId(), taskId, "REPLACE", before, before + 1,
				"事件绑定草稿已替换并标记待重发；未发布、未写 Outbox、旧快照未变化");
		return result(task, affected);
	}

	/**
	 * Joins a parent Task draft save in the same transaction. A null payload preserves
	 * existing rows; on a media-parent change it explicitly disables incompatible rows.
	 * Product defaults are materialized only when the caller sends the explicit, scoped
	 * template rows, so absence of ocpx:task:event:edit cannot create bindings implicitly.
	 */
	@Transactional
	public void syncWithinTaskSave(OcpxTask task, List<EventRequests.TaskBindingInput> inputs, boolean creation,
			boolean mediaParentChanged) {
		if (inputs == null && !mediaParentChanged) {
			return;
		}
		List<EventRequests.TaskBindingInput> effective = inputs;
		if (effective == null) {
			effective = List.of();
		}
		sync(task, effective, creation);
	}

	public EventViews.SnapshotPreview previewSnapshot(String taskId) {
		OcpxTask task = requireTask(taskId, OcpxAccessScope.PermissionLevel.VIEW, false);
		List<EventViews.SnapshotItem> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (OcpxTaskEventBinding binding : activeBindings(taskId)) {
			try {
				validateBinding(task,
						new EventRequests.TaskBindingInput(binding.getEventDefinitionId(),
								binding.getChannelEventMappingId(), binding.getCallbackRatioBasisPoints(),
								binding.getLimitValue(), binding.getOverLimitDelivery()));
			}
			catch (IllegalArgumentException error) {
				errors.add("存在已失效或不兼容的事件绑定，请重新选择后再发布");
				continue;
			}
			OcpxEventDefinition definition = definitionMapper.selectById(binding.getEventDefinitionId());
			OcpxChannelEventMapping mapping = mappingMapper.selectById(binding.getChannelEventMappingId());
			List<String> values = List.of(task.getId(), String.valueOf(version(task.getConfigVersion())),
					binding.getId(), String.valueOf(version(binding.getConfigVersion())), definition.getId(),
					String.valueOf(version(definition.getConfigVersion())), mapping.getId(),
					String.valueOf(version(mapping.getConfigVersion())), definition.getEventCode(),
					mapping.getExternalCode(), String.valueOf(binding.getCallbackRatioBasisPoints()),
					binding.getLimitValue() == null ? "UNLIMITED" : binding.getLimitValue().toString(),
					String.valueOf(Boolean.TRUE.equals(binding.getOverLimitDelivery())), SAMPLING_ALGORITHM);
			items.add(new EventViews.SnapshotItem(binding.getId(), version(binding.getConfigVersion()),
					definition.getId(), version(definition.getConfigVersion()), mapping.getId(),
					version(mapping.getConfigVersion()), definition.getEventCode(), "DOWNSTREAM_DELIVERY",
					mapping.getExternalCode(), mapping.getParameterTemplateCode(),
					binding.getCallbackRatioBasisPoints(), binding.getLimitValue(), LIMIT_WINDOW, LIMIT_TIMEZONE,
					Boolean.TRUE.equals(binding.getOverLimitDelivery()), SAMPLING_ALGORITHM,
					sha256(String.join("|", values))));
		}
		if (items.isEmpty()) {
			errors.add("任务尚未配置可发布的标准事件绑定，或现有绑定已失效");
		}
		return new EventViews.SnapshotPreview(2, "CANONICAL", taskId, version(task.getConfigVersion()), true, false,
				false, items, errors);
	}

	private void sync(OcpxTask task, List<EventRequests.TaskBindingInput> inputs, boolean creation) {
		Map<String, EventRequests.TaskBindingInput> desired = new LinkedHashMap<>();
		for (EventRequests.TaskBindingInput input : inputs) {
			if (desired.putIfAbsent(input.eventDefinitionId(), input) != null) {
				throw conflict("OCPX_TASK_EVENT_DUPLICATE：同一 Task 的标准事件不能重复绑定");
			}
			validateBinding(task, input);
		}
		List<OcpxTaskEventBinding> existing = bindingMapper.selectList(Wrappers.<OcpxTaskEventBinding>lambdaQuery()
			.eq(OcpxTaskEventBinding::getTaskId, task.getId())
			.orderByAsc(OcpxTaskEventBinding::getCreateTime));
		Map<String, OcpxTaskEventBinding> existingByDefinition = new HashMap<>();
		existing.forEach(row -> existingByDefinition.put(row.getEventDefinitionId(), row));
		for (OcpxTaskEventBinding row : existing) {
			if (!desired.containsKey(row.getEventDefinitionId()) && "ACTIVE".equals(row.getStatus())) {
				row.setStatus("DISABLED");
				row.setConfigVersion(version(row.getConfigVersion()) + 1);
				row.setUpdateBy(actor());
				bindingMapper.updateById(row);
			}
		}
		for (EventRequests.TaskBindingInput input : desired.values()) {
			OcpxTaskEventBinding row = existingByDefinition.get(input.eventDefinitionId());
			long before = row == null ? 0 : version(row.getConfigVersion());
			if (row == null) {
				row = new OcpxTaskEventBinding();
				row.setTaskId(task.getId());
				row.setEventDefinitionId(input.eventDefinitionId());
				row.setCreateBy(actor());
				row.setConfigVersion(1L);
			}
			else {
				row.setConfigVersion(before + 1);
			}
			row.setChannelId(task.getChannelId());
			row.setChannelEventMappingId(input.channelEventMappingId());
			row.setCallbackRatioBasisPoints(input.callbackRatioBasisPoints());
			row.setLimitValue(input.limitValue());
			row.setLimitWindow(LIMIT_WINDOW);
			row.setLimitTimezone(LIMIT_TIMEZONE);
			row.setOverLimitDelivery(Boolean.TRUE.equals(input.overLimitDelivery()));
			row.setSamplingAlgorithm(SAMPLING_ALGORITHM);
			DefaultSource source = creation ? defaultSource(task, input) : null;
			row.setSourceType(source == null ? "MANUAL" : "PRODUCT_DEFAULT");
			row.setSourceId(source == null ? null : source.policyId());
			row.setSourceRevision(source == null ? 0L : source.revision());
			row.setStatus("ACTIVE");
			row.setUpdateBy(actor());
			try {
				if (before == 0) {
					bindingMapper.insert(row);
				}
				else {
					bindingMapper.updateById(row);
				}
			}
			catch (DuplicateKeyException duplicate) {
				throw conflict("OCPX_TASK_EVENT_DUPLICATE：同一 Task 的标准事件不能重复绑定");
			}
		}
		if (!inputs.isEmpty() || !existing.isEmpty()) {
			audit("TASK_EVENT_BINDING", task.getId(), task.getChannelId(), task.getId(),
					creation ? "MATERIALIZE_CREATE_DRAFT" : "SYNC_PARENT_DRAFT", 0, version(task.getConfigVersion()),
					"事件绑定随 Task 草稿保存；冻结精确 revision 的发布合同由 S7 执行");
		}
	}

	private void validateBinding(OcpxTask task, EventRequests.TaskBindingInput input) {
		if (input.callbackRatioBasisPoints() == null || input.callbackRatioBasisPoints() < 0
				|| input.callbackRatioBasisPoints() > 10000) {
			throw new IllegalArgumentException("回调比例必须在 0 到 10000 basis points 之间");
		}
		if (input.limitValue() != null && input.limitValue() < 0) {
			throw new IllegalArgumentException("事件限额不能为负数；null 表示不限，0 表示全部超量");
		}
		if (input.overLimitDelivery() == null) {
			throw new IllegalArgumentException("必须明确配置超量后是否继续投递");
		}
		OcpxEventDefinition definition = definitionMapper.selectById(input.eventDefinitionId());
		OcpxChannelEventMapping mapping = mappingMapper.selectById(input.channelEventMappingId());
		if (definition == null || mapping == null || !"CANONICAL".equals(definition.getMappingState())
				|| !"CANONICAL".equals(mapping.getMappingState()) || !"ACTIVE".equals(definition.getStatus())
				|| !"ACTIVE".equals(mapping.getStatus())) {
			throw new IllegalArgumentException("标准事件或渠道映射不存在、未启用或无权访问");
		}
		if (!definition.getId().equals(mapping.getEventDefinitionId())
				|| !task.getChannelId().equals(mapping.getChannelId())
				|| !"DOWNSTREAM_DELIVERY".equals(mapping.getDirection())) {
			throw new IllegalArgumentException("渠道映射必须属于 Task 当前渠道、指向同一标准事件且方向为下游投递");
		}
		OcpxChannel channel = channelMapper.selectById(task.getChannelId());
		if (channel == null || !"ACTIVE".equals(channel.getStatus())
				|| !channel.getAdapterCode().equals(mapping.getAdapterCode())) {
			throw new IllegalArgumentException("渠道或 Adapter 已停用，或映射与 Task 当前 Adapter 不一致");
		}
	}

	private DefaultSource defaultSource(OcpxTask task, EventRequests.TaskBindingInput input) {
		List<OcpxProductEventPolicy> policies = productPolicyMapper
			.selectList(Wrappers.<OcpxProductEventPolicy>lambdaQuery()
				.eq(OcpxProductEventPolicy::getProductId, task.getProductId())
				.eq(OcpxProductEventPolicy::getEventDefinitionId, input.eventDefinitionId())
				.eq(OcpxProductEventPolicy::getStatus, "ACTIVE"));
		if (policies.size() != 1 || input.callbackRatioBasisPoints() != 10000) {
			return null;
		}
		OcpxProductEventPolicy policy = policies.get(0);
		Long policyLimit = policy.getDailyLimit() == null ? null : policy.getDailyLimit().longValue();
		if (!java.util.Objects.equals(policyLimit, input.limitValue())) {
			return null;
		}
		return new DefaultSource(policy.getId(), version(policy.getVersion()));
	}

	private List<EventViews.ProductTemplate> productTemplates(String productId, List<OcpxChannelEventMapping> mappings,
			Map<String, OcpxEventDefinition> definitions) {
		if (!StringUtils.hasText(productId)) {
			return List.of();
		}
		List<OcpxProductEventPolicy> policies = productPolicyMapper
			.selectList(Wrappers.<OcpxProductEventPolicy>lambdaQuery()
				.eq(OcpxProductEventPolicy::getProductId, productId)
				.eq(OcpxProductEventPolicy::getStatus, "ACTIVE")
				.orderByAsc(OcpxProductEventPolicy::getCreateTime));
		return policies.stream().map(policy -> {
			OcpxEventDefinition definition = definitions.get(policy.getEventDefinitionId());
			List<OcpxChannelEventMapping> matches = mappings.stream()
				.filter(mapping -> policy.getEventDefinitionId().equals(mapping.getEventDefinitionId()))
				.toList();
			boolean applicable = definition != null && matches.size() == 1;
			return new EventViews.ProductTemplate(policy.getId(), version(policy.getVersion()),
					policy.getEventDefinitionId(), definition == null ? "LEGACY_UNMAPPED" : definition.getEventCode(),
					definition == null ? "旧策略待补标准事件" : definition.getEventName(),
					applicable ? matches.get(0).getId() : null,
					policy.getDailyLimit() == null ? null : policy.getDailyLimit().longValue(), applicable,
					applicable ? "仅在创建 Task 时复制为可追踪草稿模板，保存后不动态重读" : "旧策略或映射不唯一，未自动套用，禁止猜测");
		}).toList();
	}

	private EventViews.TaskBindingsResult result(OcpxTask task, int affected) {
		return new EventViews.TaskBindingsResult(task.getId(), version(task.getConfigVersion()),
				Boolean.TRUE.equals(task.getRepublishRequired()), affected,
				activeBindings(task.getId()).stream().map(this::view).toList());
	}

	private List<OcpxTaskEventBinding> activeBindings(String taskId) {
		return bindingMapper.selectList(Wrappers.<OcpxTaskEventBinding>lambdaQuery()
			.eq(OcpxTaskEventBinding::getTaskId, taskId)
			.eq(OcpxTaskEventBinding::getStatus, "ACTIVE")
			.orderByAsc(OcpxTaskEventBinding::getCreateTime));
	}

	private EventViews.TaskBindingView view(OcpxTaskEventBinding row) {
		OcpxEventDefinition definition = definitionMapper.selectById(row.getEventDefinitionId());
		OcpxChannelEventMapping mapping = mappingMapper.selectById(row.getChannelEventMappingId());
		return new EventViews.TaskBindingView(row.getId(), row.getTaskId(), row.getChannelId(),
				row.getEventDefinitionId(), definition == null ? "-" : definition.getEventCode(),
				definition == null ? "-" : definition.getEventName(),
				definition == null ? 0 : version(definition.getConfigVersion()), row.getChannelEventMappingId(),
				mapping == null ? "-" : mapping.getExternalCode(),
				mapping == null ? 0 : version(mapping.getConfigVersion()), row.getCallbackRatioBasisPoints(),
				row.getLimitValue(), row.getLimitWindow(), row.getLimitTimezone(),
				Boolean.TRUE.equals(row.getOverLimitDelivery()), row.getSamplingAlgorithm(), row.getSourceType(),
				row.getSourceId(), version(row.getSourceRevision()), row.getStatus(), version(row.getConfigVersion()));
	}

	private OcpxTask requireTask(String taskId, OcpxAccessScope.PermissionLevel level, boolean lock) {
		OcpxTask task = lock
				? taskMapper.selectOne(Wrappers.<OcpxTask>lambdaQuery().eq(OcpxTask::getId, taskId).last("FOR UPDATE"))
				: taskMapper.selectById(taskId);
		if (task == null) {
			throw new IllegalArgumentException("任务不存在或无权访问");
		}
		accessScope.require(OcpxAccessScope.ScopeType.ADVERTISER, task.getAdvertiserId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.CHANNEL, task.getChannelId(), level);
		accessScope.require(OcpxAccessScope.ScopeType.MEDIA_ACCOUNT, task.getMediaAccountId(), level);
		return task;
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
		row.setChangeSummary(summary);
		auditMapper.insert(row);
	}

	private String actor() {
		String value = SecurityUtils.getUserName();
		return StringUtils.hasText(value) ? value : "unknown";
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
			throw new IllegalStateException("无法构建事件快照指纹", error);
		}
	}

	private BootBusinessException conflict(String message) {
		return new BootBusinessException(HttpStatus.CONFLICT.value(), message);
	}

	private record DefaultSource(String policyId, long revision) {
	}

}
