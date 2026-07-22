package com.yzytech.scx.delivery.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.delivery.entity.*;
import com.yzytech.scx.delivery.mapper.*;
import com.yzytech.scx.subject.service.MediaScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeliveryConfigService {

	private static final Set<String> EVENTS = Set.of("CLICK", "IMPRESSION", "ACTIVATE", "REGISTER", "PAY", "ORDER",
			"NEXT_DAY_RETURN", "RETAIN_3D", "RETAIN_7D", "FIRST_ACTIVATE", "FIRST_ORDER", "REACTIVATE", "CUSTOM",
			"CREDIT");

	private static final Set<String> LINK_STATES = Set.of("DRAFT", "ACTIVE", "DISABLED", "ARCHIVED");

	private static final Set<String> TASK_STATES = Set.of("DRAFT", "ACTIVE", "PAUSED", "ARCHIVED");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}|\\$\\{([a-z_]+)}");

	private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of("click_id", "event_id", "device_key_hash",
			"task_code", "timestamp");

	private final MediaScopeService scopeService;

	private final MasterDataService masterDataService;

	private final PartnerMapper partnerMapper;

	private final UpstreamCustomerMapper customerMapper;

	private final ProductMapper productMapper;

	private final IntegrationDocumentMapper documentMapper;

	private final MediaChannelMapper channelMapper;

	private final TrackingLinkMapper linkMapper;

	private final LinkEventMappingMapper mappingMapper;

	private final TrackingTaskMapper taskMapper;

	private final RtaEndpointMapper rtaEndpointMapper;

	private final TaskScopeService taskScopeService;

	private final DeliveryConfigRevisionMapper revisionMapper;

	private final DeliveryStatusHistoryMapper statusHistoryMapper;

	private final ObjectMapper objectMapper;

	@Transactional
	public boolean saveLink(TrackingLink value) {
		requireWrite(value.getMediaId());
		if (value.getId() != null) {
			TrackingLink existing = requireLink(value.getId(), true);
			if (!"DRAFT".equals(existing.getStatus()))
				throw new IllegalArgumentException("已启用链接配置不可覆盖，请创建新链接");
			if (!existing.getMediaId().equals(value.getMediaId()))
				throw new IllegalArgumentException("资源不存在");
			value.setConfigVersion(existing.getConfigVersion() + 1);
		}
		Product product = productMapper.selectById(value.getProductId());
		Partner partner = partnerMapper.selectById(value.getPartnerId());
		if (product == null || partner == null || !value.getMediaId().equals(product.getMediaId())
				|| !value.getMediaId().equals(partner.getMediaId())
				|| !partner.getId().equals(product.getPartnerId())) {
			throw new IllegalArgumentException("产品与合作方归属不一致");
		}
		if (StringUtils.hasText(product.getCustomerId())) {
			UpstreamCustomer customer = customerMapper.selectById(product.getCustomerId());
			if (customer == null || !value.getMediaId().equals(customer.getMediaId())
					|| !"ACTIVE".equals(customer.getStatus())) {
				throw new IllegalArgumentException("产品绑定的上游客户不可用");
			}
			value.setCustomerId(customer.getId());
		}
		if (StringUtils.hasText(value.getDocumentId())) {
			IntegrationDocument document = documentMapper.selectById(value.getDocumentId());
			if (document == null || !value.getMediaId().equals(document.getMediaId()))
				throw new IllegalArgumentException("接入文档不在当前范围");
		}
		masterDataService.validateSandboxUrl(value.getTargetUrl());
		if (StringUtils.hasText(value.getDownloadUrl()))
			masterDataService.validateSandboxUrl(value.getDownloadUrl());
		validateTemplate(value.getTemplateText());
		if (value.getRedirectEnabled() == null)
			value.setRedirectEnabled(false);
		if (value.getStatus() == null)
			value.setStatus("DRAFT");
		if (value.getConfigVersion() == null)
			value.setConfigVersion(1);
		if (value.getVersion() == null)
			value.setVersion(0);
		boolean saved = value.getId() == null ? linkMapper.insert(value) == 1 : linkMapper.updateById(value) == 1;
		if (saved)
			recordRevision("LINK", value.getId(), value.getConfigVersion(), value);
		return saved;
	}

	@Transactional
	public int replaceMappings(String linkId, List<LinkEventMapping> mappings) {
		TrackingLink link = requireLink(linkId, true);
		if ("ARCHIVED".equals(link.getStatus()))
			throw new IllegalArgumentException("已归档链接不可修改事件映射");
		int next = link.getConfigVersion() + 1;
		for (LinkEventMapping mapping : mappings) {
			String event = mapping.getInternalEvent().toUpperCase();
			if (!EVENTS.contains(event))
				throw new IllegalArgumentException("不支持的标准事件: " + event);
			if (mapping.getUnitPrice() == null)
				mapping.setUnitPrice(BigDecimal.ZERO);
			if (mapping.getUnitPrice().signum() < 0)
				throw new IllegalArgumentException("事件单价不能小于 0");
			if (mapping.getDailyLimit() != null && mapping.getDailyLimit() < 1) {
				throw new IllegalArgumentException("事件日限量必须大于 0");
			}
			mapping.setInternalEvent(event);
			mapping.setLinkId(linkId);
			mapping.setConfigVersion(next);
			mappingMapper.insert(mapping);
		}
		link.setConfigVersion(next);
		if (linkMapper.updateById(link) != 1)
			throw new IllegalArgumentException("链接版本已变更，请刷新");
		recordRevision("LINK", link.getId(), next, link);
		return next;
	}

	@Transactional
	public boolean saveTask(TrackingTask value) {
		requireWrite(value.getMediaId());
		if (value.getId() != null) {
			TrackingTask existing = requireTask(value.getId(), true);
			if (!"DRAFT".equals(existing.getStatus()))
				throw new IllegalArgumentException("已启用任务配置不可覆盖，请创建新任务");
			if (!existing.getMediaId().equals(value.getMediaId()))
				throw new IllegalArgumentException("资源不存在");
			value.setConfigVersion(existing.getConfigVersion() + 1);
		}
		TrackingLink link = linkMapper.selectById(value.getLinkId());
		MediaChannel channel = channelMapper.selectById(value.getChannelId());
		if (link == null || channel == null || !value.getMediaId().equals(link.getMediaId())
				|| !value.getMediaId().equals(channel.getMediaId())) {
			throw new IllegalArgumentException("链接与渠道必须属于同一媒体");
		}
		if (value.getDedupWindowSec() == null)
			value.setDedupWindowSec(0);
		if (value.getAttributionWindowHours() == null)
			value.setAttributionWindowHours(168);
		if (value.getImpressionEnabled() == null)
			value.setImpressionEnabled(false);
		if (value.getRtaEnabled() == null)
			value.setRtaEnabled(false);
		if (value.getBigClickEnabled() == null)
			value.setBigClickEnabled(false);
		if (Boolean.TRUE.equals(value.getRtaEnabled())) {
			RtaEndpoint endpoint = rtaEndpointMapper.selectById(value.getRtaEndpointId());
			if (endpoint == null || !value.getMediaId().equals(endpoint.getMediaId())
					|| !"ACTIVE".equals(endpoint.getStatus())) {
				throw new IllegalArgumentException("后置 RTA 必须绑定当前媒体范围内已启用的 RTA 配置");
			}
		}
		else {
			value.setRtaEndpointId(null);
		}
		if (Boolean.TRUE.equals(value.getImpressionEnabled())) {
			if (!StringUtils.hasText(value.getExposureReportUrl()))
				throw new IllegalArgumentException("开启曝光监测后必须填写曝光上报地址");
			masterDataService.validateSandboxUrl(value.getExposureReportUrl());
		}
		else {
			value.setExposureReportUrl(null);
			value.setBigClickEnabled(false);
		}
		if (value.getCallbackTargetRef() == null)
			value.setCallbackTargetRef("sandbox");
		if (!value.getCallbackTargetRef().matches("[A-Za-z0-9._-]{1,64}")) {
			throw new IllegalArgumentException("回传目标标识格式无效");
		}
		if (value.getStatus() == null)
			value.setStatus("DRAFT");
		if (value.getConfigVersion() == null)
			value.setConfigVersion(1);
		if (value.getVersion() == null)
			value.setVersion(0);
		boolean saved = value.getId() == null ? taskMapper.insert(value) == 1 : taskMapper.updateById(value) == 1;
		if (saved)
			recordRevision("TASK", value.getId(), value.getConfigVersion(), value);
		return saved;
	}

	@Transactional
	public boolean changeLinkStatus(String id, String status, String reason, Integer expectedVersion) {
		TrackingLink link = requireLink(id, true);
		status = status.toUpperCase();
		if (!LINK_STATES.contains(status) || !allowedLinkTransition(link.getStatus(), status))
			throw new IllegalArgumentException("非法链接状态迁移");
		if (expectedVersion != null && !expectedVersion.equals(link.getVersion()))
			throw new IllegalArgumentException("链接版本已变更");
		String previous = link.getStatus();
		link.setStatus(status);
		if ("ARCHIVED".equals(status))
			link.setArchivedAt(LocalDateTime.now());
		boolean changed = linkMapper.updateById(link) == 1;
		if (changed)
			recordStatus("LINK", id, previous, status, reason, link.getVersion());
		return changed;
	}

	@Transactional
	public boolean changeTaskStatus(String id, String status, String reason, Integer expectedVersion) {
		TrackingTask task = requireTask(id, true);
		status = status.toUpperCase();
		if (!TASK_STATES.contains(status) || !allowedTaskTransition(task.getStatus(), status))
			throw new IllegalArgumentException("非法任务状态迁移");
		if (expectedVersion != null && !expectedVersion.equals(task.getVersion()))
			throw new IllegalArgumentException("任务版本已变更");
		if ("ACTIVE".equals(status)) {
			TrackingLink link = linkMapper.selectById(task.getLinkId());
			MediaChannel channel = channelMapper.selectById(task.getChannelId());
			if (link == null || !"ACTIVE".equals(link.getStatus()) || channel == null
					|| !"ACTIVE".equals(channel.getStatus()))
				throw new IllegalArgumentException("启用任务前链接与渠道必须已启用");
			if (Boolean.TRUE.equals(task.getRtaEnabled())) {
				RtaEndpoint endpoint = rtaEndpointMapper.selectById(task.getRtaEndpointId());
				if (endpoint == null || !task.getMediaId().equals(endpoint.getMediaId())
						|| !"ACTIVE".equals(endpoint.getStatus())) {
					throw new IllegalArgumentException("启用任务前绑定的 RTA 配置必须仍处于启用状态");
				}
			}
		}
		String previous = task.getStatus();
		task.setStatus(status);
		if ("ARCHIVED".equals(status))
			task.setArchivedAt(LocalDateTime.now());
		boolean changed = taskMapper.updateById(task) == 1;
		if (changed)
			recordStatus("TASK", id, previous, status, reason, task.getVersion());
		return changed;
	}

	public TrackingLink requireLink(String id, boolean write) {
		TrackingLink link = linkMapper.selectById(id);
		if (link == null
				|| (write ? !scopeService.canWrite(link.getMediaId()) : !scopeService.canRead(link.getMediaId())))
			throw new IllegalArgumentException("资源不存在");
		return link;
	}

	public TrackingTask requireTask(String id, boolean write) {
		TrackingTask task = taskMapper.selectById(id);
		if (task == null || !(write ? taskScopeService.canWrite(task) : taskScopeService.canRead(task)))
			throw new IllegalArgumentException("资源不存在");
		return task;
	}

	public String resolveActiveLink(String code) {
		TrackingLink link = linkMapper.selectOne(Wrappers.<TrackingLink>lambdaQuery()
			.eq(TrackingLink::getCode, code)
			.eq(TrackingLink::getStatus, "ACTIVE")
			.last("LIMIT 1"));
		if (link == null)
			throw new IllegalArgumentException("追踪链接不存在或未启用");
		return link.getTargetUrl();
	}

	private void validateTemplate(String text) {
		if (!StringUtils.hasText(text))
			return;
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			String key = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
			if (!ALLOWED_PLACEHOLDERS.contains(key))
				throw new IllegalArgumentException("未知链接占位符: " + key);
		}
	}

	private void requireWrite(String mediaId) {
		if (!scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void recordRevision(String type, String id, Integer configVersion, Object snapshot) {
		DeliveryConfigRevision revision = new DeliveryConfigRevision();
		revision.setResourceType(type);
		revision.setResourceId(id);
		revision.setConfigVersion(configVersion);
		try {
			revision.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("配置快照序列化失败");
		}
		revisionMapper.insert(revision);
	}

	private void recordStatus(String type, String id, String previous, String next, String reason, Integer version) {
		DeliveryStatusHistory history = new DeliveryStatusHistory();
		history.setResourceType(type);
		history.setResourceId(id);
		history.setPreviousStatus(previous);
		history.setNextStatus(next);
		history.setReason(reason);
		history.setOperationVersion(version);
		statusHistoryMapper.insert(history);
	}

	private boolean allowedLinkTransition(String from, String to) {
		return from.equals(to) || ("DRAFT".equals(from) && "ACTIVE".equals(to))
				|| (Set.of("ACTIVE", "DISABLED").contains(from)
						&& Set.of("ACTIVE", "DISABLED", "ARCHIVED").contains(to));
	}

	private boolean allowedTaskTransition(String from, String to) {
		return from.equals(to) || ("DRAFT".equals(from) && "ACTIVE".equals(to))
				|| (Set.of("ACTIVE", "PAUSED").contains(from) && Set.of("ACTIVE", "PAUSED", "ARCHIVED").contains(to));
	}

}
