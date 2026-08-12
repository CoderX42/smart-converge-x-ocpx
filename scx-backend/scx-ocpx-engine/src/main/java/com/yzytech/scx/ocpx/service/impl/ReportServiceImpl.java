package com.yzytech.scx.ocpx.service.impl;

import com.yzytech.scx.ocpx.bean.LogData;
import com.yzytech.scx.ocpx.dao.mapper.CallbackMapper;
import com.yzytech.scx.ocpx.dao.mapper.FeedbackMapper;
import com.yzytech.scx.ocpx.service.wrapper.ReportService;
import org.springframework.stereotype.Service;

import java.util.List;

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
		feedbackMapper.addLogFeedback(data);
		feedbackMapper.addLogFeedbackDay(data);
		feedbackMapper.addReport(data);
	}

	@Override
	public void addCallback(List<LogData> data) {
		callbackMapper.addLogCallback(data);
		callbackMapper.addReport(data);
	}

}
