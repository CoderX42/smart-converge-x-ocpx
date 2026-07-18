package com.yzytech.scx.tracking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.dto.AttributionReviewRequest;
import com.yzytech.scx.tracking.dto.AttributionReviewView;
import com.yzytech.scx.tracking.entity.AttributionResult;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.RawEvent;
import com.yzytech.scx.tracking.mapper.AttributionResultMapper;
import com.yzytech.scx.tracking.mapper.AttributionReviewMapper;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AttributionReviewService {

	private static final Set<String> REVIEW_STATES = Set.of("APPROVED", "REDUCED");

	private final AttributionReviewMapper reviewMapper;

	private final AttributionResultMapper attributionMapper;

	private final RawEventMapper rawEventMapper;

	private final TrackingTaskMapper taskMapper;

	private final CallbackJobMapper callbackMapper;

	private final MediaScopeService scopeService;

	private final TaskScopeService taskScopeService;

	public IPage<AttributionReviewView> page(Page<AttributionReviewView> page, String mediaId, String reviewStatus,
			Boolean overLimit, String taskId, LocalDate startDate, LocalDate endDate, String callbackStatus) {
		TaskScopeService.TaskAccess taskAccess = taskScopeService.accessForMedia(mediaId, false);
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("转化开始日期不能晚于结束日期");
		}
		return reviewMapper.selectScopedPage(page, mediaId, reviewStatus, overLimit, taskId, startDate, endDate,
				callbackStatus, taskAccess.restricted(), taskAccess.taskIds());
	}

	@Transactional
	public boolean review(String attributionId, String mediaId, AttributionReviewRequest request) {
		requireWrite(mediaId);
		if (!mediaId.equals(reviewMapper.selectMediaId(attributionId)))
			throw new IllegalArgumentException("资源不存在");
		String taskId = reviewMapper.selectTaskId(attributionId, mediaId);
		TrackingTask scopedTask = taskId == null ? null : taskMapper.selectById(taskId);
		if (scopedTask == null || !taskScopeService.canWrite(scopedTask))
			throw new IllegalArgumentException("资源不存在");
		AttributionResult attribution = attributionMapper.selectById(attributionId);
		if (attribution == null || !"ATTRIBUTED".equals(attribution.getStatus()))
			throw new IllegalArgumentException("该转化不可审核");
		if (!"PENDING".equals(attribution.getReviewStatus()))
			throw new IllegalArgumentException("该转化已完成审核");
		String status = request.status().trim().toUpperCase(Locale.ROOT);
		if (!REVIEW_STATES.contains(status))
			throw new IllegalArgumentException("不支持的审核结论");
		if ("REDUCED".equals(status) && !StringUtils.hasText(request.reason()))
			throw new IllegalArgumentException("审减必须填写原因");

		attribution.setReviewStatus(status);
		attribution.setReviewReason("REDUCED".equals(status) ? request.reason().trim() : null);
		attribution.setReviewedBy(SecurityUtils.getUserId());
		attribution.setReviewedAt(LocalDateTime.now());
		if (attributionMapper.updateById(attribution) != 1)
			throw new IllegalArgumentException("审核状态已变更，请刷新");

		RawEvent conversion = rawEventMapper.selectById(attribution.getConversionEventId());
		TrackingTask task = conversion == null ? null : taskMapper.selectById(conversion.getTaskId());
		if (task == null || !mediaId.equals(task.getMediaId()))
			throw new IllegalArgumentException("资源不存在");
		if ("REDUCED".equals(status))
			enqueueCallback(attribution.getId(), "REDUCTION", task.getCallbackTargetRef());
		if ("APPROVED".equals(status) && Boolean.TRUE.equals(attribution.getOverLimit()))
			enqueueCallback(attribution.getId(), "ORIGINAL", task.getCallbackTargetRef());
		return true;
	}

	private void enqueueCallback(String attributionId, String action, String targetRef) {
		CallbackJob job = new CallbackJob();
		job.setAttributionResultId(attributionId);
		job.setActionType(action);
		job.setTargetRef(targetRef);
		job.setStatus("PENDING");
		job.setAttempts(0);
		try {
			callbackMapper.insert(job);
		}
		catch (DuplicateKeyException ignored) {
			// The unique key keeps a repeated HTTP request from sending a duplicate
			// correction.
		}
	}

	private void requireRead(String mediaId) {
		if (mediaId == null || !scopeService.canRead(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

	private void requireWrite(String mediaId) {
		if (mediaId == null || !scopeService.canWrite(mediaId))
			throw new IllegalArgumentException("资源不存在");
	}

}
