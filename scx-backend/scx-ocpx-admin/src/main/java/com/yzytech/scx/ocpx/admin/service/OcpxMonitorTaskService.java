package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.handler.BootBusinessException;
import com.yzytech.scx.ocpx.admin.config.OcpxTrackingProperties;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskRequests;
import com.yzytech.scx.ocpx.admin.dto.MonitorTaskViews;
import com.yzytech.scx.ocpx.admin.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.admin.entity.OcpxMedia;
import com.yzytech.scx.ocpx.admin.entity.OcpxMonitorTask;
import com.yzytech.scx.ocpx.admin.entity.OcpxPromotionLink;
import com.yzytech.scx.ocpx.admin.mapper.OcpxAdvertiserMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMediaMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxMonitorTaskMapper;
import com.yzytech.scx.ocpx.admin.mapper.OcpxPromotionLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OcpxMonitorTaskService {

	private final OcpxMonitorTaskMapper taskMapper;
	private final OcpxAdvertiserMapper advertiserMapper;
	private final OcpxPromotionLinkMapper linkMapper;
	private final OcpxMediaMapper mediaMapper;
	private final OcpxTrackingProperties trackingProperties;

	public IPage<MonitorTaskViews.Item> list(Page<OcpxMonitorTask> page, String keyword, String advertiserId,
			String promotionLinkId, String mediaId, String status) {
		return taskMapper.selectPage(page, Wrappers.<OcpxMonitorTask>lambdaQuery()
			.and(StringUtils.hasText(keyword), q -> q.like(OcpxMonitorTask::getTaskCode, keyword).or()
				.like(OcpxMonitorTask::getTaskName, keyword))
			.eq(StringUtils.hasText(advertiserId), OcpxMonitorTask::getAdvertiserId, advertiserId)
			.eq(StringUtils.hasText(promotionLinkId), OcpxMonitorTask::getPromotionLinkId, promotionLinkId)
			.eq(StringUtils.hasText(mediaId), OcpxMonitorTask::getMediaId, mediaId)
			.eq(StringUtils.hasText(status), OcpxMonitorTask::getStatus, status)
			.orderByDesc(OcpxMonitorTask::getUpdateTime)).convert(this::view);
	}

	public MonitorTaskViews.Item detail(String id) { return view(require(id)); }

	@Transactional
	public MonitorTaskViews.Item create(MonitorTaskRequests.Create request) {
		ensureCode(null, request.taskCode()); validateRelation(request.advertiserId(), request.promotionLinkId(), request.mediaId());
		OcpxMonitorTask task = new OcpxMonitorTask(); task.setTaskCode(request.taskCode().trim());
		apply(task, request.taskName(), request.advertiserId(), request.promotionLinkId(), request.mediaId(), request.operatorUserId());
		task.setStatus("ACTIVE"); task.setConfigVersion(1L); task.setDeleted(false); taskMapper.insert(task);
		return detail(task.getId());
	}

	@Transactional
	public MonitorTaskViews.Item update(String id, MonitorTaskRequests.Update request) {
		OcpxMonitorTask current = require(id); requireVersion(current, request.expectedVersion());
		validateRelation(request.advertiserId(), request.promotionLinkId(), request.mediaId());
		OcpxMonitorTask update = new OcpxMonitorTask(); update.setId(id);
		apply(update, request.taskName(), request.advertiserId(), request.promotionLinkId(), request.mediaId(), request.operatorUserId());
		update.setConfigVersion(current.getConfigVersion() + 1);
		if (taskMapper.update(update, Wrappers.<OcpxMonitorTask>lambdaUpdate().eq(OcpxMonitorTask::getId, id)
			.eq(OcpxMonitorTask::getConfigVersion, request.expectedVersion())) != 1) throw conflict();
		return detail(id);
	}

	@Transactional
	public MonitorTaskViews.Mutation changeStatus(String id, MonitorTaskRequests.ChangeStatus request) {
		if (!java.util.Set.of("ACTIVE", "PAUSED").contains(request.status())) throw new IllegalArgumentException("任务状态仅支持 ACTIVE/PAUSED");
		OcpxMonitorTask current = require(id); requireVersion(current, request.expectedVersion()); long next = current.getConfigVersion() + 1;
		if (taskMapper.update(null, Wrappers.<OcpxMonitorTask>lambdaUpdate().eq(OcpxMonitorTask::getId,id)
			.eq(OcpxMonitorTask::getConfigVersion,request.expectedVersion()).set(OcpxMonitorTask::getStatus,request.status())
			.set(OcpxMonitorTask::getConfigVersion,next)) != 1) throw conflict();
		return new MonitorTaskViews.Mutation(id,request.status(),next);
	}

	@Transactional
	public MonitorTaskViews.Mutation archive(String id, MonitorTaskRequests.VersionedAction request) {
		OcpxMonitorTask current=require(id); requireVersion(current,request.expectedVersion()); long next=current.getConfigVersion()+1;
		if (taskMapper.update(null, Wrappers.<OcpxMonitorTask>lambdaUpdate().eq(OcpxMonitorTask::getId,id)
			.eq(OcpxMonitorTask::getConfigVersion,request.expectedVersion())
			.set(OcpxMonitorTask::getStatus,"ARCHIVED").set(OcpxMonitorTask::getArchivedAt, LocalDateTime.now())
			.set(OcpxMonitorTask::getConfigVersion,next).set(OcpxMonitorTask::getDeleted,true)) != 1) throw conflict();
		return new MonitorTaskViews.Mutation(id,"ARCHIVED",next);
	}

	private void validateRelation(String advertiserId,String linkId,String mediaId) {
		if (advertiserMapper.selectCount(Wrappers.<OcpxAdvertiser>lambdaQuery().eq(OcpxAdvertiser::getId,advertiserId).eq(OcpxAdvertiser::getStatus,"ACTIVE"))!=1) throw new IllegalArgumentException("广告主不存在或未启用");
		if (linkMapper.selectCount(Wrappers.<OcpxPromotionLink>lambdaQuery().eq(OcpxPromotionLink::getId,linkId).eq(OcpxPromotionLink::getAdvertiserId,advertiserId).eq(OcpxPromotionLink::getStatus,"ACTIVE"))!=1) throw new IllegalArgumentException("推广链接不属于当前广告主或未启用");
		if (mediaMapper.selectCount(Wrappers.<OcpxMedia>lambdaQuery().eq(OcpxMedia::getId,mediaId).eq(OcpxMedia::getStatus,"ACTIVE"))!=1) throw new IllegalArgumentException("媒体不存在或未启用");
	}
	private void apply(OcpxMonitorTask task,String name,String advertiserId,String linkId,String mediaId,String operator){ task.setTaskName(name.trim());task.setAdvertiserId(advertiserId);task.setPromotionLinkId(linkId);task.setMediaId(mediaId);task.setOperatorUserId(StringUtils.hasText(operator)?operator.trim():null); }
	private void ensureCode(String id,String code){if(taskMapper.selectCount(Wrappers.<OcpxMonitorTask>lambdaQuery().eq(OcpxMonitorTask::getTaskCode,code.trim()).ne(StringUtils.hasText(id),OcpxMonitorTask::getId,id))>0)throw new IllegalArgumentException("任务编码已存在");}
	private OcpxMonitorTask require(String id){OcpxMonitorTask v=taskMapper.selectById(id);if(v==null)throw new BootBusinessException(HttpStatus.NOT_FOUND.value(),"监测任务不存在");return v;}
	private void requireVersion(OcpxMonitorTask value,Long expected){if(!expected.equals(value.getConfigVersion()))throw conflict();}
	private BootBusinessException conflict(){return new BootBusinessException(409,"配置已被修改，请刷新后重试");}
	private MonitorTaskViews.Item view(OcpxMonitorTask t){OcpxAdvertiser a=advertiserMapper.selectById(t.getAdvertiserId());OcpxPromotionLink p=linkMapper.selectById(t.getPromotionLinkId());OcpxMedia m=mediaMapper.selectById(t.getMediaId());String url=trackingProperties.getPublicBaseUrl().replaceAll("/$","")+"/c/"+t.getTaskCode();return new MonitorTaskViews.Item(t.getId(),t.getTaskCode(),t.getTaskName(),t.getAdvertiserId(),a==null?null:a.getAdvertiserName(),t.getPromotionLinkId(),p==null?null:p.getLinkName(),t.getMediaId(),m==null?null:m.getMediaName(),t.getOperatorUserId(),t.getStatus(),t.getConfigVersion(),url,t.getArchivedAt(),t.getCreateTime(),t.getUpdateTime());}
}
