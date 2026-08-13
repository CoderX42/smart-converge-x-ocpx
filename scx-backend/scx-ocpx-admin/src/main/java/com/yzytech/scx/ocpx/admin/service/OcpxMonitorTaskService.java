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
import java.time.LocalDateTime;

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
			String promotionLinkId, String mediaId, String status) {
		return taskMapper.selectPage(page, Wrappers.<OcpxMonitorTask>lambdaQuery()
			.and(StringUtils.hasText(keyword),
					q -> q.like(OcpxMonitorTask::getTaskCode, keyword).or().like(OcpxMonitorTask::getTaskName, keyword))
			.eq(StringUtils.hasText(advertiserId), OcpxMonitorTask::getAdvertiserId, advertiserId)
			.eq(StringUtils.hasText(promotionLinkId), OcpxMonitorTask::getPromotionLinkId, promotionLinkId)
			.eq(StringUtils.hasText(mediaId), OcpxMonitorTask::getMediaId, mediaId)
			.eq(StringUtils.hasText(status), OcpxMonitorTask::getStatus, status)
			.orderByDesc(OcpxMonitorTask::getUpdateTime)).convert(this::view);
	}

	public MonitorTaskViews.Item detail(String id) {
		return view(require(id));
	}

	@Transactional
	public MonitorTaskViews.Item create(MonitorTaskRequests.Create request) {
		ensureCode(null, request.taskCode());
		validateRelation(request.advertiserId(), request.promotionLinkId(), request.mediaId());
		OcpxMonitorTask task = new OcpxMonitorTask();
		task.setTaskCode(request.taskCode().trim());
		apply(task, request.taskName(), request.advertiserId(), request.promotionLinkId(), request.mediaId(),
				request.operatorUserId(), request.unitPrice(), request.bizId(), request.remark());
		task.setStatus("ACTIVE");
		task.setConfigVersion(1L);
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
		requireVersion(current, request.expectedVersion());
		validateRelation(request.advertiserId(), request.promotionLinkId(), request.mediaId());
		OcpxMonitorTask update = new OcpxMonitorTask();
		update.setId(id);
		apply(update, request.taskName(), request.advertiserId(), request.promotionLinkId(), request.mediaId(),
				request.operatorUserId(), request.unitPrice(), request.bizId(), request.remark());
		update.setConfigVersion(current.getConfigVersion() + 1);
		generateTrackingUrls(update);
		if (taskMapper.update(update,
				Wrappers.<OcpxMonitorTask>lambdaUpdate()
					.eq(OcpxMonitorTask::getId, id)
					.eq(OcpxMonitorTask::getConfigVersion, request.expectedVersion())) != 1)
			throw conflict();
		configRefreshPublisher.mark();
		return detail(id);
	}

	@Transactional
	public MonitorTaskViews.Mutation changeStatus(String id, MonitorTaskRequests.ChangeStatus request) {
		if (!java.util.Set.of("ACTIVE", "PAUSED").contains(request.status()))
			throw new IllegalArgumentException("任务状态仅支持 ACTIVE/PAUSED");
		OcpxMonitorTask current = require(id);
		requireVersion(current, request.expectedVersion());
		long next = current.getConfigVersion() + 1;
		if (taskMapper.update(null,
				Wrappers.<OcpxMonitorTask>lambdaUpdate()
					.eq(OcpxMonitorTask::getId, id)
					.eq(OcpxMonitorTask::getConfigVersion, request.expectedVersion())
					.set(OcpxMonitorTask::getStatus, request.status())
					.set(OcpxMonitorTask::getConfigVersion, next)) != 1)
			throw conflict();
		configRefreshPublisher.mark();
		return new MonitorTaskViews.Mutation(id, request.status(), next);
	}

	@Transactional
	public MonitorTaskViews.Mutation archive(String id, MonitorTaskRequests.VersionedAction request) {
		OcpxMonitorTask current = require(id);
		requireVersion(current, request.expectedVersion());
		long next = current.getConfigVersion() + 1;
		if (taskMapper.update(null,
				Wrappers.<OcpxMonitorTask>lambdaUpdate()
					.eq(OcpxMonitorTask::getId, id)
					.eq(OcpxMonitorTask::getConfigVersion, request.expectedVersion())
					.set(OcpxMonitorTask::getStatus, "ARCHIVED")
					.set(OcpxMonitorTask::getArchivedAt, LocalDateTime.now())
					.set(OcpxMonitorTask::getConfigVersion, next)
					.set(OcpxMonitorTask::getDeleted, true)) != 1)
			throw conflict();
		configRefreshPublisher.mark();
		return new MonitorTaskViews.Mutation(id, "ARCHIVED", next);
	}

	/** 逻辑删（对齐 SGYZ channel/monitor/delete/{monitorId}，实体 @TableLogic 生效，URL 不做特殊处理）。 */
	@Transactional
	public void delete(String id) {
		require(id);
		taskMapper.deleteById(id);
		configRefreshPublisher.mark();
	}

	private void validateRelation(String advertiserId, String linkId, String mediaId) {
		if (advertiserMapper.selectCount(Wrappers.<OcpxAdvertiser>lambdaQuery()
			.eq(OcpxAdvertiser::getId, advertiserId)
			.eq(OcpxAdvertiser::getStatus, "ACTIVE")) != 1)
			throw new IllegalArgumentException("广告主不存在或未启用");
		if (linkMapper.selectCount(Wrappers.<OcpxPromotionLink>lambdaQuery()
			.eq(OcpxPromotionLink::getId, linkId)
			.eq(OcpxPromotionLink::getAdvertiserId, advertiserId)
			.eq(OcpxPromotionLink::getStatus, "ACTIVE")) != 1)
			throw new IllegalArgumentException("推广链接不属于当前广告主或未启用");
		if (mediaMapper.selectCount(Wrappers.<OcpxMedia>lambdaQuery()
			.eq(OcpxMedia::getId, mediaId)
			.eq(OcpxMedia::getStatus, "ACTIVE")) != 1)
			throw new IllegalArgumentException("媒体不存在或未启用");
	}

	private void apply(OcpxMonitorTask task, String name, String advertiserId, String linkId, String mediaId,
			String operator, BigDecimal unitPrice, String bizId, String remark) {
		task.setTaskName(name.trim());
		task.setAdvertiserId(advertiserId);
		task.setPromotionLinkId(linkId);
		task.setMediaId(mediaId);
		task.setOperatorUserId(StringUtils.hasText(operator) ? operator.trim() : null);
		task.setUnitPrice(unitPrice);
		task.setBizId(StringUtils.hasText(bizId) ? bizId.trim() : null);
		task.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
	}

	/**
	 * 按 SGYZ 迁移的拼装方式持久化双监测链接：引擎域名 + 媒体 ingressUri + 媒体宏参数 + event + 四实体路由参数。
	 */
	private void generateTrackingUrls(OcpxMonitorTask task) {
		OcpxMedia media = mediaMapper.selectById(task.getMediaId());
		String base = trackingProperties.getEngineBaseUrl().replaceAll("/$", "");
		String macros = media.getMacroParams() == null ? "" : media.getMacroParams();
		StringBuilder splicing = new StringBuilder();
		splicing.append("&mId=").append(task.getMediaId());
		splicing.append("&tId=").append(task.getId());
		splicing.append("&aId=").append(task.getAdvertiserId());
		splicing.append("&lId=").append(task.getPromotionLinkId());
		String prefix = base + media.getIngressUri() + macros;
		task.setShowUrl(prefix + "&event=IMP" + splicing);
		task.setClkUrl(prefix + "&event=CLK" + splicing);
	}

	private void ensureCode(String id, String code) {
		if (taskMapper.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery()
			.eq(OcpxMonitorTask::getTaskCode, code.trim())
			.ne(StringUtils.hasText(id), OcpxMonitorTask::getId, id)) > 0)
			throw new IllegalArgumentException("任务编码已存在");
	}

	private OcpxMonitorTask require(String id) {
		OcpxMonitorTask v = taskMapper.selectById(id);
		if (v == null)
			throw new BootBusinessException(HttpStatus.NOT_FOUND.value(), "监测任务不存在");
		return v;
	}

	private void requireVersion(OcpxMonitorTask value, Long expected) {
		if (!expected.equals(value.getConfigVersion()))
			throw conflict();
	}

	private BootBusinessException conflict() {
		return new BootBusinessException(409, "配置已被修改，请刷新后重试");
	}

	private MonitorTaskViews.Item view(OcpxMonitorTask t) {
		OcpxAdvertiser a = advertiserMapper.selectById(t.getAdvertiserId());
		OcpxPromotionLink p = linkMapper.selectById(t.getPromotionLinkId());
		OcpxMedia m = mediaMapper.selectById(t.getMediaId());
		return new MonitorTaskViews.Item(t.getId(), t.getTaskCode(), t.getTaskName(), t.getAdvertiserId(),
				a == null ? null : a.getAdvertiserName(), t.getPromotionLinkId(), p == null ? null : p.getLinkName(),
				t.getMediaId(), m == null ? null : m.getMediaName(), t.getOperatorUserId(), t.getStatus(),
				t.getConfigVersion(), t.getShowUrl(), t.getClkUrl(), t.getUnitPrice(), t.getBizId(), t.getRemark(),
				t.getArchivedAt(), t.getCreateTime(), t.getUpdateTime());
	}

}
