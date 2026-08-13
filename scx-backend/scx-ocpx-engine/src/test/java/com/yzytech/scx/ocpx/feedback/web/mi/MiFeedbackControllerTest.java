package com.yzytech.scx.ocpx.feedback.web.mi;

import com.yzytech.scx.ocpx.feedback.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MiFeedbackControllerTest {

	@Test
	void writesTheFixedMiSuccessResponse() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = mvc(feedbackService);

		mvc.perform(get("/ocpx/mi/feedback").contextPath("/ocpx")
			.param("mId", "media-id")
			.param("tId", "task-id")
			.param("aId", "advertiser-id")
			.param("lId", "link-id")
			.param("event", "CLK")
			.param("request_id", "request-1"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("application/json"))
			.andExpect(content().string("{\"code\": 0, \"failMsg\":\"\", \"success\":true}"));
		verify(feedbackService).handleFeedback(any());
	}

	@Test
	void retainsTheCompatibleEmptyResponseWhenFeedbackHandlingFails() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		doThrow(new IllegalStateException("feedback failed")).when(feedbackService).handleFeedback(any());
		MockMvc mvc = mvc(feedbackService);

		mvc.perform(get("/ocpx/mi/feedback").contextPath("/ocpx")
			.param("mId", "media-id")
			.param("tId", "task-id")
			.param("aId", "advertiser-id")
			.param("lId", "link-id")
			.param("event", "CLK"))
			.andExpect(status().isOk())
			.andExpect(content().string(""));
	}

	private MockMvc mvc(FeedbackService feedbackService) {
		return MockMvcBuilders.standaloneSetup(new MiFeedbackController(feedbackService)).build();
	}

}
