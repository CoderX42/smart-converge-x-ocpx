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

@Service
@RequiredArgsConstructor
public class OcpxMonitorTaskService {

	private final OcpxMonitorTaskMapper taskMapper;

	private final OcpxAdvertiserMapper advertiserMapper;

	private final OcpxPromotionLinkMapper linkMapper;

	private final OcpxMediaMapper mediaMapper;

	private final OcpxTrackingProperties trackingProperties;

	private final OcpxConfigRefreshPublisher configRefreshPublisher;

	public IPage<MonitorTaskViews.Item> list(Page<OcpxMonitorTask> page, String keyword, String advertiserId,
			String promotionLinkId, String mediaId, String monitorTaskId) {
		return taskMapper.selectPage(page, Wrappers.<OcpxMonitorTask>lambdaQuery()
			.and(StringUtils.hasText(keyword),
					q -> q.like(OcpxMonitorTask::getId, keyword).or().like(OcpxMonitorTask::getMonitorName, keyword))
			.eq(StringUtils.hasText(advertiserId), OcpxMonitorTask::getAdvertiserId, advertiserId)
			.eq(StringUtils.hasText(promotionLinkId), OcpxMonitorTask::getPromotionLinkId, promotionLinkId)
			.eq(StringUtils.hasText(mediaId), OcpxMonitorTask::getMediaId, mediaId)
			.eq(StringUtils.hasText(monitorTaskId), OcpxMonitorTask::getId, monitorTaskId)
			.orderByDesc(OcpxMonitorTask::getUpdateTime)).convert(this::view);
	}

	public MonitorTaskViews.Item detail(String id) {
		return view(require(id));
	}

	@Transactional
	public MonitorTaskViews.Item create(MonitorTaskRequests.Create request) {
		validateRelation(request.advertiserId(), request.promotionLinkId(), request.mediaId());
		OcpxMonitorTask task = new OcpxMonitorTask();
		apply(task, request.monitorName(), request.advertiserId(), request.promotionLinkId(), request.mediaId(),
				request.unitPrice(), request.bizId(), request.remark());
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
		require(id);
		validateRelation(request.advertiserId(), request.promotionLinkId(), request.mediaId());
		OcpxMonitorTask update = new OcpxMonitorTask();
		update.setId(id);
		apply(update, request.monitorName(), request.advertiserId(), request.promotionLinkId(), request.mediaId(),
				request.unitPrice(), request.bizId(), request.remark());
		generateTrackingUrls(update);
		taskMapper.updateById(update);
		configRefreshPublisher.mark();
		return detail(id);
	}

	/** 逻辑删（对齐 SGYZ channel/monitor/delete/{monitorId}，实体 @TableLogic 生效，URL 不做特殊处理）。 */
	@Transactional
	public void delete(String id) {
		require(id);
		taskMapper.deleteById(id);
		configRefreshPublisher.mark();
	}

	private void validateRelation(String advertiserId, String linkId, String mediaId) {
		if (advertiserMapper.selectById(advertiserId) == null)
			throw new IllegalArgumentException("广告主不存在");
		if (linkMapper.selectCount(Wrappers.<OcpxPromotionLink>lambdaQuery()
			.eq(OcpxPromotionLink::getId, linkId)
			.eq(OcpxPromotionLink::getAdvertiserId, advertiserId)) != 1)
			throw new IllegalArgumentException("推广链接不属于当前广告主");
		if (mediaMapper.selectById(mediaId) == null)
			throw new IllegalArgumentException("媒体不存在");
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

	private String normalizeQuery(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		String query = value.trim();
		while (query.startsWith("?") || query.startsWith("&")) {
			query = query.substring(1);
		}
		while (query.endsWith("&")) {
			query = query.substring(0, query.length() - 1);
		}
		return query;
	}

	private OcpxMonitorTask require(String id) {
		OcpxMonitorTask v = taskMapper.selectById(id);
		if (v == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "监测任务不存在");
		return v;
	}

	private MonitorTaskViews.Item view(OcpxMonitorTask t) {
		OcpxAdvertiser a = advertiserMapper.selectById(t.getAdvertiserId());
		OcpxPromotionLink p = linkMapper.selectById(t.getPromotionLinkId());
		OcpxMedia m = mediaMapper.selectById(t.getMediaId());
		return new MonitorTaskViews.Item(t.getId(), t.getMonitorName(), t.getAdvertiserId(),
				a == null ? null : a.getAdvertiserName(), t.getPromotionLinkId(), p == null ? null : p.getLinkName(),
				p == null ? null : p.getPlatform(), t.getMediaId(), m == null ? null : m.getMediaName(), t.getShowUrl(),
				t.getClkUrl(), t.getUnitPrice(), t.getBizId(), t.getRemark(), t.getCreateTime(), t.getUpdateTime());
	}

}
