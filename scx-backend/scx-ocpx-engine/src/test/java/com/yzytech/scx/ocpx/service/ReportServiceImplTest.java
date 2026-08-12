package com.yzytech.scx.ocpx.service;

import com.yzytech.scx.ocpx.bean.LogData;
import com.yzytech.scx.ocpx.dao.mapper.CallbackMapper;
import com.yzytech.scx.ocpx.dao.mapper.FeedbackMapper;
import com.yzytech.scx.ocpx.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ReportServiceImplTest {

	@Test
	void keepsTheLegacyFeedbackAndCallbackWriteOrder() {
		FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
		CallbackMapper callbackMapper = mock(CallbackMapper.class);
		ReportServiceImpl service = new ReportServiceImpl(feedbackMapper, callbackMapper);
		List<LogData> data = List.of(new LogData());

		service.addFeedback(data);
		service.addCallback(data);

		var ordered = inOrder(feedbackMapper, callbackMapper);
		ordered.verify(feedbackMapper).addLogFeedback(data);
		ordered.verify(feedbackMapper).addLogFeedbackDay(data);
		ordered.verify(feedbackMapper).addReport(data);
		ordered.verify(callbackMapper).addLogCallback(data);
		ordered.verify(callbackMapper).addReport(data);
	}

}
