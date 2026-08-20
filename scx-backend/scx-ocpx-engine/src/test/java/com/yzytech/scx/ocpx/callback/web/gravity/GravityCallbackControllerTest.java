package com.yzytech.scx.ocpx.callback.web.gravity;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackDecision;
import com.yzytech.scx.ocpx.callback.service.CallbackHandleResult;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GravityCallbackControllerTest {

	@Test
	void returnsExactSuccessJsonForForwardedDuplicateAndDeductedCallbacks() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		GravityCallbackAdapter adapter = mock(GravityCallbackAdapter.class);
		CallbackReq request = new CallbackReq();
		request.setFeedback(FeedbackReq.builder().reqId("request-id").build());
		when(adapter.toCallback("token")).thenReturn(request);
		when(callbackService.handleCallback(any()))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.FORWARDED))
			.thenReturn(new CallbackHandleResult(true, CallbackDecision.NOT_APPLICABLE))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.DEDUCTED));
		MockMvc mvc = mvc(callbackService, adapter);

		for (int index = 0; index < 3; index++) {
			mvc.perform(get("/gravity/callback").param("ctx", "token"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"code\":200,\"result\":\"success\"}"));
		}
	}

	@Test
	void returnsExactFailedJsonForMissingTamperedAndInternalFailures() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		GravityCallbackAdapter adapter = mock(GravityCallbackAdapter.class);
		when(adapter.toCallback(any())).thenThrow(new IllegalArgumentException("invalid"));
		MockMvc mvc = mvc(callbackService, adapter);

		mvc.perform(get("/gravity/callback"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().string("{\"code\":400,\"result\":\"failed\"}"));
		mvc.perform(get("/gravity/callback").param("ctx", "tampered"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string("{\"code\":400,\"result\":\"failed\"}"));

		reset(adapter);
		when(adapter.toCallback(any())).thenThrow(new IllegalStateException("internal"));
		mvc.perform(get("/gravity/callback").param("ctx", "token"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string("{\"code\":400,\"result\":\"failed\"}"));
	}

	private MockMvc mvc(CallbackService callbackService, GravityCallbackAdapter adapter) {
		return MockMvcBuilders.standaloneSetup(new GravityCallbackController(callbackService, adapter)).build();
	}

}
