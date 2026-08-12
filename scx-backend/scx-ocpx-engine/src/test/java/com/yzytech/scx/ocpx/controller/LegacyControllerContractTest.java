package com.yzytech.scx.ocpx.controller;

import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.controller.general.GeneralController;
import com.yzytech.scx.ocpx.manager.union.UnionController;
import com.yzytech.scx.ocpx.manager.union.UnionParamAdapter;
import com.yzytech.scx.ocpx.service.wrapper.CallbackService;
import com.yzytech.scx.ocpx.service.wrapper.FeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyControllerContractTest {

	@Test
	void exposesGeneralFeedbackUnderTheScxContext() throws Exception {
		FeedbackService feedbackService = mock(FeedbackService.class);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new GeneralController(feedbackService)).build();

		mvc.perform(get("/ocpx/general/feedback").contextPath("/ocpx")
			.param("chid", "media-id")
			.param("mid", "task-id")
			.param("dspid", "advertiser-id")
			.param("linkid", "link-id")
			.param("event", "CLK")
			.param("request_id", "request-1"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"ret\":0,\"code\":200,\"msg\":\"提交成功\",\"data\":{}}"));
		verify(feedbackService).handleFeedback(any());
	}

	@Test
	void exposesUnionCallbackUnderTheScxContext() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		UnionParamAdapter adapter = mock(UnionParamAdapter.class);
		when(adapter.getCallbackReq(any())).thenReturn(new CallbackReq());
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new UnionController(callbackService, adapter)).build();

		mvc.perform(get("/ocpx/union/callback").contextPath("/ocpx")
			.param("ext", "encrypted")
			.param("event_type", "1")
			.param("event_time", "1")).andExpect(status().isOk()).andExpect(content().string(""));
		verify(callbackService).handleFeedback(any());
	}

	@Test
	void returnsFiveHundredWhenTheUnionCallbackCannotBeParsed() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		UnionParamAdapter adapter = mock(UnionParamAdapter.class);
		when(adapter.getCallbackReq(any())).thenThrow(new IllegalArgumentException("invalid ext"));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new UnionController(callbackService, adapter)).build();

		mvc.perform(get("/ocpx/union/callback").contextPath("/ocpx")
			.param("ext", "invalid")
			.param("event_type", "1")
			.param("event_time", "1")).andExpect(status().isInternalServerError()).andExpect(content().string(""));
	}

}
