package com.yzytech.scx.ocpx.report.service;

import com.yzytech.scx.ocpx.report.model.LogData;
import com.yzytech.scx.ocpx.report.mapper.CallbackMapper;
import com.yzytech.scx.ocpx.report.mapper.FeedbackMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

	@Test
	void wrapsEachRedisBatchInOneDatabaseTransaction() throws Exception {
		Method feedback = ReportServiceImpl.class.getMethod("addFeedback", List.class);
		Method callback = ReportServiceImpl.class.getMethod("addCallback", List.class);

		assertThat(feedback.isAnnotationPresent(Transactional.class)).isTrue();
		assertThat(callback.isAnnotationPresent(Transactional.class)).isTrue();
	}

	@Test
	void defaultsMissingCallbackCurrencyAmountToZeroInTheAggregateMapper() throws Exception {
		String mapper;
		try (var input = ReportServiceImplTest.class.getResourceAsStream("/mapper/CallbackMapper.xml")) {
			assertThat(input).isNotNull();
			mapper = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}

		assertThat(mapper).contains("#{item.standardActionType},#{item.actionTime},#{item.currencyAmount})")
			.contains("#{item.firstActivation},#{item.firstOrder},#{item.download},#{item.activity},#{item.addiction},\n"
					+ "             IFNULL(#{item.currencyAmount},0))");
	}

}
