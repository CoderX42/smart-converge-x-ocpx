package com.yzytech.scx.ocpx.report.service;

import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.report.mapper.CallbackMapper;
import com.yzytech.scx.ocpx.report.mapper.FeedbackMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 报表批量落库实现。
 *
 * <p>
 * 每个 Redis 批次的多表写入使用同一事务；任一步失败时全部回滚并由 {@code StorageTask} 保留原批次。
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
	@Transactional
	public void addFeedback(List<LogData> data) {
		// 写入顺序属于现有数据链路契约，三步全部成功后 StorageTask 才确认消费。
		feedbackMapper.addLogFeedback(data);
		feedbackMapper.addLogFeedbackDay(data);
		feedbackMapper.addReport(data);
	}

	@Override
	@Transactional
	public void addCallback(List<LogData> data) {
		// Callback 明细与临时报表原子写入，避免报表失败时重试产生重复明细。
		callbackMapper.addLogCallback(data);
		callbackMapper.addReport(data);
	}

}
