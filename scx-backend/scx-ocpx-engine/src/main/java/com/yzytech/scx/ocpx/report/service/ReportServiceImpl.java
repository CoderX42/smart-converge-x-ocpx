package com.yzytech.scx.ocpx.report.service;

import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.report.mapper.CallbackMapper;
import com.yzytech.scx.ocpx.report.mapper.FeedbackMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报表批量落库实现。
 *
 * <p>
 * 方法不自行开启跨表事务；任一步失败会抛给 {@code StorageTask}，后者因此不会裁剪 Redis List。
 */
@Service
public class ReportServiceImpl implements ReportService {

	private final FeedbackMapper feedbackMapper;

	private final CallbackMapper callbackMapper;

	public ReportServiceImpl(FeedbackMapper feedbackMapper, CallbackMapper callbackMapper) {
		this.feedbackMapper = feedbackMapper;
		this.callbackMapper = callbackMapper;
	}

	@Override
	public void addFeedback(List<LogData> data) {
		// 写入顺序属于现有数据链路契约，StorageTask 仅在三步全部成功后确认消费。
		feedbackMapper.addLogFeedback(data);
		feedbackMapper.addLogFeedbackDay(data);
		feedbackMapper.addReport(data);
	}

	@Override
	public void addCallback(List<LogData> data) {
		// Callback 明细成功后继续累加临时报表，任一步异常均保留 Redis 原批次。
		callbackMapper.addLogCallback(data);
		callbackMapper.addReport(data);
	}

}
