package com.yzytech.scx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.reporting.entity.ExportTask;
import com.yzytech.scx.reporting.mapper.ExportTaskMapper;
import com.yzytech.scx.reporting.service.ExportTaskService;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.dto.CallbackQueueStats;
import com.yzytech.scx.tracking.dto.OutboxQueueStats;
import com.yzytech.scx.tracking.entity.CallbackJob;
import com.yzytech.scx.tracking.entity.EventOutbox;
import com.yzytech.scx.tracking.service.CallbackJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system/async-services")
@Tag(description = "async", name = "异步服务运行态")
public class AsyncServicesController {

	private static final String QUEUE_CALLBACK = "callback";

	private static final String QUEUE_EXPORT = "export";

	private static final String QUEUE_OUTBOX = "outbox";

	private final CallbackJobService callbackJobService;

	private final ExportTaskService exportTaskService;

	private final ExportTaskMapper exportTaskMapper;

	private final MediaScopeService mediaScopeService;

	@GetMapping("/stats")
	@SaCheckPermission("ad:queue:read")
	@Operation(summary = "异步队列统计")
	public Result<AsyncQueueStats> stats(@RequestParam String queueType, @RequestParam String mediaId) {
		try {
			QueueType type = resolveQueueType(queueType);
			requireMediaRead(mediaId);
			return switch (type) {
				case CALLBACK -> Result.success(buildCallbackStats(mediaId));
				case EXPORT -> Result.success(buildExportStats(mediaId));
				case OUTBOX -> Result.success(buildOutboxStats(mediaId));
			};
		}
		catch (IllegalArgumentException exception) {
			return Result.fail(exception.getMessage());
		}
	}

	@GetMapping("/list")
	@SaCheckPermission("ad:queue:read")
	@Operation(summary = "异步队列列表")
	public Result<IPage<?>> list(Page<?> page, @RequestParam String queueType, @RequestParam String mediaId, String status,
			String rawEventId, String aggregateId) {
		try {
			QueueType type = resolveQueueType(queueType);
			requireMediaRead(mediaId);
			return switch (type) {
				case CALLBACK -> Result.success(
					callbackJobService.page(createCallbackPage(page), mediaId, status, rawEventId));
				case OUTBOX -> Result.success(
					callbackJobService.outboxPage(createOutboxPage(page), mediaId, status, aggregateId));
				case EXPORT -> Result.success(exportTaskService.page(createExportPage(page), mediaId, status));
			};
		}
		catch (IllegalArgumentException exception) {
			return Result.fail(exception.getMessage());
		}
	}

	private CallbackQueueStats getCallbackStats(String mediaId) {
		return callbackJobService.stats(mediaId);
	}

	private OutboxQueueStats getOutboxStats(String mediaId) {
		return callbackJobService.stats(mediaId).getOutbox();
	}

	private AsyncQueueStats buildCallbackStats(String mediaId) {
		CallbackQueueStats source = getCallbackStats(mediaId);
		AsyncQueueStats result = new AsyncQueueStats();
		result.setWaitingCount(nullToZero(source.getPendingCount()));
		result.setRunningCount(nullToZero(source.getRetryingCount()));
		result.setSucceededCount(nullToZero(source.getSucceededCount()));
		result.setFailedCount(0L);
		result.setDelayedCount(nullToZero(source.getDelayedCount()));
		result.setPausedCount(nullToZero(source.getPausedCount()));
		result.setDeadCount(nullToZero(source.getDeadCount()));
		return result;
	}

	private AsyncQueueStats buildOutboxStats(String mediaId) {
		OutboxQueueStats source = getOutboxStats(mediaId);
		AsyncQueueStats result = new AsyncQueueStats();
		result.setWaitingCount(nullToZero(source.getPendingCount()));
		result.setRunningCount(nullToZero(source.getRetryingCount()));
		result.setSucceededCount(nullToZero(source.getSentCount()));
		result.setFailedCount(0L);
		result.setDelayedCount(nullToZero(source.getDelayedCount()));
		result.setPausedCount(0L);
		result.setDeadCount(nullToZero(source.getDeadCount()));
		return result;
	}

	private AsyncQueueStats buildExportStats(String mediaId) {
		AsyncQueueStats result = new AsyncQueueStats();
		result.setWaitingCount(countExportTask(mediaId, "PENDING"));
		result.setRunningCount(countExportTask(mediaId, "RUNNING"));
		result.setSucceededCount(countExportTask(mediaId, "SUCCEEDED"));
		result.setFailedCount(countExportTask(mediaId, "FAILED"));
		result.setDelayedCount(0L);
		result.setPausedCount(0L);
		result.setDeadCount(countExportTask(mediaId, "EXPIRED"));
		return result;
	}

	private long countExportTask(String mediaId, String status) {
		return exportTaskMapper
			.selectCount(new LambdaQueryWrapper<ExportTask>().eq(ExportTask::getMediaId, mediaId).eq(ExportTask::getStatus, status));
	}

	private void requireMediaRead(String mediaId) {
		if (mediaId == null || !mediaScopeService.canRead(mediaId)) {
			throw new IllegalArgumentException("资源不存在");
		}
	}

	private QueueType resolveQueueType(String queueType) {
		if (queueType == null) {
			throw new IllegalArgumentException("queueType 不能为空");
		}
		switch (queueType.toLowerCase()) {
			case QUEUE_CALLBACK:
				return QueueType.CALLBACK;
			case QUEUE_EXPORT:
				return QueueType.EXPORT;
			case QUEUE_OUTBOX:
				return QueueType.OUTBOX;
			default:
				throw new IllegalArgumentException("不支持的 queueType");
		}
	}

	private Page<CallbackJob> createCallbackPage(Page<?> page) {
		return new Page<>(page == null ? 1 : page.getCurrent(), page == null ? 20 : page.getSize());
	}

	private Page<ExportTask> createExportPage(Page<?> page) {
		return new Page<>(page == null ? 1 : page.getCurrent(), page == null ? 20 : page.getSize());
	}

	private Page<EventOutbox> createOutboxPage(Page<?> page) {
		return new Page<>(page == null ? 1 : page.getCurrent(), page == null ? 20 : page.getSize());
	}

	@Data
	private static class AsyncQueueStats {

		private long waitingCount;

		private long runningCount;

		private long succeededCount;

		private long failedCount;

		private long delayedCount;

		private long pausedCount;

		private long deadCount;

	}

	private enum QueueType {
		CALLBACK,
		EXPORT,
		OUTBOX
	}

	private long nullToZero(Long value) {
		return value == null ? 0L : value;
	}
}
*** End Patch
