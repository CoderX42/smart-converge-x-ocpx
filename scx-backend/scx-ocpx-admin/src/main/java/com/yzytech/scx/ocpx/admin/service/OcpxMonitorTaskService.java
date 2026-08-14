package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskViews;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import com.yzytech.scx.ocpx.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
		advertiserScope.requireOwner(request.getAdvertiserId());
		validateRelation(request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId());
		OcpxMonitorTask task = new OcpxMonitorTask();
		apply(task, request.getMonitorName(), request.getAdvertiserId(), request.getPromotionLinkId(),
				request.getMediaId(), request.getUnitPrice(), request.getBizId(), request.getRemark());
		task.setDeleted(false);
		taskMapper.insert(task);
		generateTrackingUrls(task);
		OcpxMonitorTask tracking = new OcpxMonitorTask();
		tracking.setId(task.getId());
		tracking.setShowUrl(task.getShowUrl());
		tracking.setClkUrl(task.getClkUrl());
		taskMapper.updateById(tracking);
		configRefreshPublisher.mark();
		return detail(task.getId());
	}

	@Transactional
	public MonitorTaskViews.Item update(String id, MonitorTaskRequests.Update request) {
		OcpxMonitorTask current = require(id);
		advertiserScope.requireOwner(current.getAdvertiserId());
		advertiserScope.requireOwner(request.getAdvertiserId());
		validateRelation(request.getAdvertiserId(), request.getPromotionLinkId(), request.getMediaId());
		OcpxMonitorTask update = new OcpxMonitorTask();
		update.setId(id);
		apply(update, request.getMonitorName(), request.getAdvertiserId(), request.getPromotionLinkId(),
				request.getMediaId(), request.getUnitPrice(), request.getBizId(), request.getRemark());
		generateTrackingUrls(update);
		taskMapper.updateById(update);
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
			BigDecimal unitPrice, String bizId, String remark) {
		task.setMonitorName(name.trim());
		task.setAdvertiserId(advertiserId);
		task.setPromotionLinkId(linkId);
		task.setMediaId(mediaId);
		task.setUnitPrice(unitPrice);
		task.setBizId(StringUtils.hasText(bizId) ? bizId.trim() : null);
		task.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
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
				t.getClkUrl(), t.getUnitPrice(), t.getBizId(), t.getRemark(), t.getCreateTime(), t.getUpdateTime());
	}

}
