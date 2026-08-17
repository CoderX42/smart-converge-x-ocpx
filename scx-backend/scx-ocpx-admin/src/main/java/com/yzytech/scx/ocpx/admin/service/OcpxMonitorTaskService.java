package com.yzytech.scx.ocpx.admin.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.model.OcpxConversionEvent;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import com.yzytech.scx.system.api.constant.SystemAccountConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OcpxMonitorTaskService {

	private static final String REQUEST_ID_PARAMETER = "request_id";

	private static final String REQUEST_ID_MACRO = REQUEST_ID_PARAMETER + "=__REQUEST_ID__";

	private final OcpxMonitorTaskMapper taskMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxPromotionLinkMapper linkMapper;

	private final OcpxMediaMapper mediaMapper;

	private final OcpxTrackingProperties trackingProperties;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	private final AdvertiserScope advertiserScope;

	private final JdbcTemplate jdbcTemplate;

	public IPage<MonitorTaskViews.Item> list(Page<OcpxMonitorTask> page, String keyword, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId) {
		String scopedAdvertiserId = advertiserScope.constrain(advertiserId);
		IPage<OcpxMonitorTask> result = taskMapper.selectPage(page, Wrappers.<OcpxMonitorTask>lambdaQuery()
			.and(StringUtils.hasText(keyword),
					q -> q.like(OcpxMonitorTask::getId, keyword).or().like(OcpxMonitorTask::getMonitorName, keyword))
			.eq(StringUtils.hasText(scopedAdvertiserId), OcpxMonitorTask::getAdvertiserId, scopedAdvertiserId)
			.eq(StringUtils.hasText(promotionLinkId), OcpxMonitorTask::getPromotionLinkId, promotionLinkId)
			.eq(StringUtils.hasText(mediaId), OcpxMonitorTask::getMediaId, mediaId)
			.eq(StringUtils.hasText(monitorTaskId), OcpxMonitorTask::getId, monitorTaskId)
			.orderByDesc(OcpxMonitorTask::getUpdateTime));
		Map<String, OcpxAdvertiser> advertisers = advertisers(result.getRecords());
		Map<String, OcpxPromotionLink> links = links(result.getRecords());
		Map<String, OcpxMedia> media = media(result.getRecords());
		return result.convert(task -> view(task, advertisers.get(task.getAdvertiserId()),
				links.get(task.getPromotionLinkId()), media.get(task.getMediaId())));
	}

	/** 对齐 SGYZ getMonitorName：按媒体过滤监测任务下拉。 */
	public List<MonitorTaskViews.Option> options(String mediaId) {
		String scopedAdvertiserId = advertiserScope.currentAdvertiserId();
		return taskMapper
			.selectList(Wrappers.<OcpxMonitorTask>lambdaQuery()
				.eq(StringUtils.hasText(mediaId), OcpxMonitorTask::getMediaId, mediaId)
				.eq(StringUtils.hasText(scopedAdvertiserId), OcpxMonitorTask::getAdvertiserId, scopedAdvertiserId)
				.orderByDesc(OcpxMonitorTask::getId))
			.stream()
			.map(item -> new MonitorTaskViews.Option(item.getId(), item.getMonitorName()))
			.toList();
	}

	public MonitorTaskViews.Item detail(String id) {
		OcpxMonitorTask task = require(id);
		advertiserScope.requireOwner(task.getAdvertiserId());
		return view(task, advertiserMapper.selectById(task.getAdvertiserId()),
				linkMapper.selectById(task.getPromotionLinkId()), mediaMapper.selectById(task.getMediaId()));
	}

	@Transactional
	public MonitorTaskViews.Item create(MonitorTaskRequests.Create request) {
		advertiserScope.requireInternalPermission(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION);
		advertiserScope.requireOwner(request.getAdvertiserId());
		validateRelation(request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId());
		validateBilling(request.getBillingEnabled(), request.getBillingType(), request.getUnitPrice(),
				request.getConversionEvent());
		OcpxMonitorTask task = new OcpxMonitorTask();
		apply(task, request.getMonitorName(), request.getAdvertiserId(), request.getPromotionLinkId(),
				request.getMediaId(), request.getBillingEnabled(), request.getBillingType(), request.getUnitPrice(),
				request.getConversionEvent(), request.getBizId(), request.getRemark());
		task.setDeleted(false);
		taskMapper.insert(task);
		generateTrackingUrls(task);
		OcpxMonitorTask tracking = new OcpxMonitorTask();
		tracking.setId(task.getId());
		tracking.setShowUrl(task.getShowUrl());
		tracking.setClkUrl(task.getClkUrl());
		taskMapper.updateById(tracking);
		writeBillingAudit("CREATE", null, task);
		configRefreshPublisher.mark();
		return detail(task.getId());
	}

	@Transactional
	public MonitorTaskViews.Item update(String id, MonitorTaskRequests.Update request) {
		OcpxMonitorTask current = require(id);
		advertiserScope.requireOwner(current.getAdvertiserId());
		advertiserScope.requireOwner(request.getAdvertiserId());
		boolean billingEnabled = request.getBillingEnabled() == null ? Boolean.TRUE.equals(current.getBillingEnabled())
				: request.getBillingEnabled();
		int billingType = request.getBillingType() == null
				? (current.getBillingType() == null ? 1 : current.getBillingType()) : request.getBillingType();
		if (billingChanged(current, billingEnabled, billingType, request.getUnitPrice(), request.getConversionEvent())) {
			advertiserScope.requireInternalPermission(SystemAccountConstants.MONITOR_TASK_BILLING_PERMISSION);
		}
		validateRelation(request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId());
		validateBilling(billingEnabled, billingType, request.getUnitPrice(), request.getConversionEvent());
		OcpxMonitorTask update = new OcpxMonitorTask();
		update.setId(id);
		apply(update, request.getMonitorName(), request.getAdvertiserId(), request.getPromotionLinkId(),
				request.getMediaId(), billingEnabled, billingType, request.getUnitPrice(), request.getConversionEvent(),
				request.getBizId(), request.getRemark());
		generateTrackingUrls(update);
		taskMapper.updateById(update);
		if (billingChanged(current, billingEnabled, billingType, request.getUnitPrice(), request.getConversionEvent())) {
			writeBillingAudit("UPDATE", current, update);
		}
		configRefreshPublisher.mark();
		return detail(id);
	}

	/** 逻辑删（对齐 SGYZ channel/monitor/delete/{monitorId}，实体 @TableLogic 生效，URL 不做特殊处理）。 */
	@Transactional
	public void delete(String id) {
		OcpxMonitorTask current = require(id);
		advertiserScope.requireOwner(current.getAdvertiserId());
		taskMapper.deleteById(id);
		configRefreshPublisher.mark();
	}

	private void validateRelation(String advertiserId, String linkId, String mediaId) {
		if (advertiserMapper.selectById(advertiserId) == null)
			throw new BootBusinessException("广告主不存在");
		if (linkMapper.selectCount(Wrappers.<OcpxPromotionLink>lambdaQuery()
			.eq(OcpxPromotionLink::getId, linkId)
			.eq(OcpxPromotionLink::getAdvertiserId, advertiserId)) != 1)
			throw new BootBusinessException("推广链接不属于当前广告主");
		if (mediaMapper.selectById(mediaId) == null)
			throw new BootBusinessException("媒体不存在");
	}

	private void apply(OcpxMonitorTask task, String name, String advertiserId, String linkId, String mediaId,
			boolean billingEnabled, int billingType, BigDecimal unitPrice, String conversionEvent, String bizId,
			String remark) {
		task.setMonitorName(name.trim());
		task.setAdvertiserId(advertiserId);
		task.setPromotionLinkId(linkId);
		task.setMediaId(mediaId);
		task.setBillingEnabled(billingEnabled);
		task.setBillingType(billingType);
		task.setUnitPrice(unitPrice);
		task.setConversionEvent(conversionEvent);
		task.setBizId(StringUtils.hasText(bizId) ? bizId.trim() : null);
		task.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
	}

	private void validateBilling(Boolean billingEnabled, Integer billingType, BigDecimal unitPrice,
			String conversionEvent) {
		if (billingEnabled == null || billingType == null || (billingType != 1 && billingType != 2)) {
			throw new BootBusinessException("计费开关或计费形式无效");
		}
		if (unitPrice == null || unitPrice.signum() < 0) {
			throw new BootBusinessException("监测任务单价不能为负数");
		}
		if (billingEnabled && unitPrice.signum() <= 0) {
			throw new BootBusinessException("开启计费时单次扣费金额必须大于 0");
		}
		if (!OcpxConversionEvent.supports(conversionEvent)) {
			throw new BootBusinessException("目标转化事件不受支持");
		}
	}

	private boolean billingChanged(OcpxMonitorTask current, boolean enabled, int type, BigDecimal price,
			String conversionEvent) {
		return Boolean.TRUE.equals(current.getBillingEnabled()) != enabled
				|| (current.getBillingType() == null ? 1 : current.getBillingType()) != type
				|| current.getUnitPrice() == null || current.getUnitPrice().compareTo(price) != 0
				|| !Objects.equals(current.getConversionEvent(), conversionEvent);
	}

	private void writeBillingAudit(String action, OcpxMonitorTask before, OcpxMonitorTask after) {
		BootUser operator = SecurityUtils.getUser();
		jdbcTemplate.update("""
				INSERT INTO scx_ocpx_billing_config_audit
				(id,monitor_task_id,advertiser_id,action_type,before_config,after_config,operator_user_id,occurred_at)
				VALUES (?,?,?,?,?,?,?,?)
				""", UUID.randomUUID().toString().replace("-", ""), after.getId(), after.getAdvertiserId(), action,
				before == null ? null : JSON.toJSONString(billingConfig(before)), JSON.toJSONString(billingConfig(after)),
				operator == null ? null : operator.getUserId(), LocalDateTime.now());
	}

	private Map<String, Object> billingConfig(OcpxMonitorTask task) {
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("billingEnabled", Boolean.TRUE.equals(task.getBillingEnabled()));
		config.put("billingType", task.getBillingType() == null ? 1 : task.getBillingType());
		config.put("unitPrice", task.getUnitPrice());
		config.put("conversionEvent", task.getConversionEvent());
		return config;
	}

	/**
	 * 按 SGYZ 迁移的拼装方式持久化双监测链接：引擎域名 + 媒体 URI + 媒体宏参数 + event + 四实体路由参数。
	 */
	private void generateTrackingUrls(OcpxMonitorTask task) {
		OcpxMedia media = mediaMapper.selectById(task.getMediaId());
		String base = trackingProperties.getEngineBaseUrl().replaceAll("/$", "");
		String macros = normalizeQuery(media.getMacroParams());
		StringBuilder splicing = new StringBuilder();
		splicing.append("&mId=").append(task.getMediaId());
		splicing.append("&tId=").append(task.getId());
		splicing.append("&aId=").append(task.getAdvertiserId());
		splicing.append("&lId=").append(task.getPromotionLinkId());
		String prefix = base + media.getUri() + "?" + (macros.isEmpty() ? "" : macros + "&");
		task.setShowUrl(prefix + "event=IMP" + splicing);
		task.setClkUrl(prefix + "event=CLK" + splicing);
	}

	/** 保证通用链路始终以唯一的 {@code request_id=__REQUEST_ID__} 开头，其余媒体宏保持原顺序。 */
	private String normalizeQuery(String value) {
		List<String> parameters = new ArrayList<>();
		parameters.add(REQUEST_ID_MACRO);
		if (!StringUtils.hasText(value)) {
			return String.join("&", parameters);
		}
		String query = value.trim();
		while (query.startsWith("?") || query.startsWith("&")) {
			query = query.substring(1);
		}
		while (query.endsWith("&")) {
			query = query.substring(0, query.length() - 1);
		}
		for (String parameter : query.split("&")) {
			String normalized = parameter.trim();
			if (!StringUtils.hasText(normalized) || REQUEST_ID_PARAMETER.equalsIgnoreCase(parameterName(normalized))) {
				continue;
			}
			parameters.add(normalized);
		}
		return String.join("&", parameters);
	}

	private String parameterName(String parameter) {
		int separator = parameter.indexOf('=');
		return (separator < 0 ? parameter : parameter.substring(0, separator)).trim();
	}

	private OcpxMonitorTask require(String id) {
		OcpxMonitorTask v = taskMapper.selectById(id);
		if (v == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "监测任务不存在");
		return v;
	}

	private Map<String, OcpxAdvertiser> advertisers(List<OcpxMonitorTask> tasks) {
		List<String> ids = tasks.stream()
			.map(OcpxMonitorTask::getAdvertiserId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		return ids.isEmpty() ? Map.of()
				: advertiserMapper.selectBatchIds(ids)
					.stream()
					.collect(Collectors.toMap(OcpxAdvertiser::getId, Function.identity()));
	}

	private Map<String, OcpxPromotionLink> links(List<OcpxMonitorTask> tasks) {
		List<String> ids = tasks.stream()
			.map(OcpxMonitorTask::getPromotionLinkId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		return ids.isEmpty() ? Map.of()
				: linkMapper.selectBatchIds(ids)
					.stream()
					.collect(Collectors.toMap(OcpxPromotionLink::getId, Function.identity()));
	}

	private Map<String, OcpxMedia> media(List<OcpxMonitorTask> tasks) {
		List<String> ids = tasks.stream()
			.map(OcpxMonitorTask::getMediaId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		return ids.isEmpty() ? Map.of()
				: mediaMapper.selectBatchIds(ids)
					.stream()
					.collect(Collectors.toMap(OcpxMedia::getId, Function.identity()));
	}

	private MonitorTaskViews.Item view(OcpxMonitorTask t, OcpxAdvertiser a, OcpxPromotionLink p, OcpxMedia m) {
		return new MonitorTaskViews.Item(t.getId(), t.getMonitorName(), t.getAdvertiserId(),
				a == null ? null : a.getAdvertiserName(), t.getPromotionLinkId(), p == null ? null : p.getLinkName(),
				p == null ? null : p.getPlatform(), t.getMediaId(), m == null ? null : m.getMediaName(), t.getShowUrl(),
				t.getClkUrl(), Boolean.TRUE.equals(t.getBillingEnabled()), t.getBillingType() == null ? 1 : t.getBillingType(),
				t.getUnitPrice(), t.getConversionEvent(), t.getBizId(), t.getRemark(), t.getCreateTime(), t.getUpdateTime());
	}

}
