package com.yzytech.scx.ocpx.contract;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import com.yzytech.scx.ocpx.callback.web.UpstreamCallbackController;
import com.yzytech.scx.ocpx.feedback.service.FeedbackService;
import com.yzytech.scx.ocpx.feedback.web.FeedbackIngressAdapter;
import com.yzytech.scx.ocpx.feedback.web.FeedbackIngressController;
import com.yzytech.scx.ocpx.protocol.CallbackRequestAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OcpxIngressContractTest {

	@Test
	void exposesFeedbackIngressUnderTheScxContext() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = feedbackMvc(feedbackService);

		mvc.perform(get("/ocpx/feedback").contextPath("/ocpx")
			.param("mId", "media-id")
			.param("tId", "task-id")
			.param("aId", "advertiser-id")
			.param("lId", "link-id")
			.param("event", "CLK")
			.param("request_id", "request-1"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("{\"ret\":0,\"code\":200,\"msg\":\"提交成功\",\"data\":{}}"));
		verify(feedbackService).handleFeedback(any());
	}

	@Test
	void retainsTheCompatibleEmptySuccessResponseWhenFeedbackHandlingFails() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		doThrow(new IllegalStateException("feedback failed")).when(feedbackService).handleFeedback(any());
		MockMvc mvc = feedbackMvc(feedbackService);

		mvc.perform(get("/ocpx/feedback").contextPath("/ocpx")
			.param("mId", "media-id")
			.param("tId", "task-id")
			.param("aId", "advertiser-id")
			.param("lId", "link-id")
			.param("event", "CLK")
			.param("request_id", "request-1")).andExpect(status().isOk()).andExpect(content().string(""));
	}

	@Test
	void rejectsRetiredFeedbackRouteParameters() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = feedbackMvc(feedbackService);

		mvc.perform(get("/ocpx/feedback").contextPath("/ocpx")
			.param("chid", "media-id")
			.param("mid", "task-id")
			.param("dspid", "advertiser-id")
			.param("linkid", "link-id")
			.param("event", "CLK"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(feedbackService);
	}

	@Test
	void rejectsMixedFeedbackRouteParameters() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = feedbackMvc(feedbackService);

		mvc.perform(get("/ocpx/feedback").contextPath("/ocpx")
			.param("mId", "media-id")
			.param("tId", "task-id")
			.param("aId", "advertiser-id")
			.param("lId", "link-id")
			.param("chid", "retired-media-id")
			.param("event", "CLK"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(feedbackService);
	}

	@Test
	void rejectsMissingFeedbackRouteParameter() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = feedbackMvc(feedbackService);

		for (String omitted : new String[] { "mId", "tId", "aId", "lId" }) {
			var request = get("/ocpx/feedback").contextPath("/ocpx").param("event", "CLK");
			if (!"mId".equals(omitted)) {
				request.param("mId", "media-id");
			}
			if (!"tId".equals(omitted)) {
				request.param("tId", "task-id");
			}
			if (!"aId".equals(omitted)) {
				request.param("aId", "advertiser-id");
			}
			if (!"lId".equals(omitted)) {
				request.param("lId", "link-id");
			}
			mvc.perform(request).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(feedbackService);
	}

	@Test
	void acceptsFeedbackRouteParametersAtTheDatabaseColumnLimit() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = feedbackMvc(feedbackService);
		String routeId = "x".repeat(32);

		mvc.perform(get("/ocpx/feedback").contextPath("/ocpx")
			.param("mId", routeId)
			.param("tId", routeId)
			.param("aId", routeId)
			.param("lId", routeId)
			.param("event", "CLK"))
			.andExpect(status().isOk());
		verify(feedbackService).handleFeedback(any());
	}

	@Test
	void rejectsFeedbackRouteParametersBeyondTheDatabaseColumnLimit() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = feedbackMvc(feedbackService);

		for (String oversized : new String[] { "mId", "tId", "aId", "lId" }) {
			var request = get("/ocpx/feedback").contextPath("/ocpx")
				.param("mId", "mId".equals(oversized) ? "x".repeat(33) : "media-id")
				.param("tId", "tId".equals(oversized) ? "x".repeat(33) : "task-id")
				.param("aId", "aId".equals(oversized) ? "x".repeat(33) : "advertiser-id")
				.param("lId", "lId".equals(oversized) ? "x".repeat(33) : "link-id")
				.param("event", "CLK");
			mvc.perform(request).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(feedbackService);
	}

	@Test
	void exposesUpstreamCallbackUnderTheScxContext() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		CallbackRequestAdapter adapter = mock(CallbackRequestAdapter.class);
		when(adapter.toCallback(any())).thenReturn(new CallbackReq());
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new UpstreamCallbackController(callbackService, adapter)).build();

		mvc.perform(get("/ocpx/callback").contextPath("/ocpx")
			.param("ext", "encrypted")
			.param("event_type", "1")
			.param("event_time", "1")).andExpect(status().isOk()).andExpect(content().string(""));
		verify(callbackService).handleCallback(any());
	}

	@Test
	void returnsFiveHundredWhenTheUpstreamCallbackCannotBeParsed() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		CallbackRequestAdapter adapter = mock(CallbackRequestAdapter.class);
		when(adapter.toCallback(any())).thenThrow(new IllegalArgumentException("invalid ext"));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new UpstreamCallbackController(callbackService, adapter)).build();

		mvc.perform(get("/ocpx/callback").contextPath("/ocpx")
			.param("ext", "invalid")
			.param("event_type", "1")
			.param("event_time", "1")).andExpect(status().isInternalServerError()).andExpect(content().string(""));
	}

	private MockMvc feedbackMvc(FeedbackService feedbackService) {
		return MockMvcBuilders
			.standaloneSetup(new FeedbackIngressController(feedbackService, new FeedbackIngressAdapter()))
			.build();
	}

}
