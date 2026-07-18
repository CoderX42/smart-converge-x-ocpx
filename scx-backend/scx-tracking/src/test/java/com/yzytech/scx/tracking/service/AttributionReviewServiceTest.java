package com.yzytech.scx.tracking.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yzytech.scx.delivery.mapper.TrackingTaskMapper;
import com.yzytech.scx.delivery.service.TaskScopeService;
import com.yzytech.scx.delivery.entity.TrackingTask;
import com.yzytech.scx.subject.service.MediaScopeService;
import com.yzytech.scx.tracking.dto.AttributionReviewRequest;
import com.yzytech.scx.tracking.entity.AttributionResult;
import com.yzytech.scx.tracking.mapper.AttributionResultMapper;
import com.yzytech.scx.tracking.mapper.AttributionReviewMapper;
import com.yzytech.scx.tracking.mapper.CallbackJobMapper;
import com.yzytech.scx.tracking.mapper.RawEventMapper;
import org.junit.jupiter.api.Test;

class AttributionReviewServiceTest {

	private final AttributionReviewMapper reviewMapper = mock(AttributionReviewMapper.class);

	private final AttributionResultMapper attributionMapper = mock(AttributionResultMapper.class);

	private final MediaScopeService scopeService = mock(MediaScopeService.class);

	private final TaskScopeService taskScopeService = mock(TaskScopeService.class);

	private final TrackingTaskMapper taskMapper = mock(TrackingTaskMapper.class);

	private final AttributionReviewService service = new AttributionReviewService(reviewMapper, attributionMapper,
			mock(RawEventMapper.class), taskMapper, mock(CallbackJobMapper.class), scopeService, taskScopeService);

	@Test
	void reductionRequiresAnExplicitReason() {
		AttributionResult attribution = new AttributionResult();
		attribution.setStatus("ATTRIBUTED");
		attribution.setReviewStatus("PENDING");
		when(scopeService.canWrite("media-1")).thenReturn(true);
		when(reviewMapper.selectMediaId("attr-1")).thenReturn("media-1");
		TrackingTask task = new TrackingTask();
		task.setId("task-1");
		task.setMediaId("media-1");
		when(reviewMapper.selectTaskId("attr-1", "media-1")).thenReturn("task-1");
		when(taskMapper.selectById("task-1")).thenReturn(task);
		when(taskScopeService.canWrite(task)).thenReturn(true);
		when(attributionMapper.selectById("attr-1")).thenReturn(attribution);

		assertThatThrownBy(() -> service.review("attr-1", "media-1", new AttributionReviewRequest("REDUCED", " ")))
			.hasMessage("审减必须填写原因");
	}

}
