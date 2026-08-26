package com.yzytech.scx.ocpx.callback.web.renrenvideo;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackDecision;
import com.yzytech.scx.ocpx.callback.service.CallbackHandleResult;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RenrenVideoCallbackControllerTest {

	@Test
	void returnsEmptyHttp200ForForwardedDuplicateAndDeductedCallbacks() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		RenrenVideoCallbackAdapter adapter = mock(RenrenVideoCallbackAdapter.class);
		when(adapter.toCallback(any())).thenReturn(new CallbackReq());
		when(callbackService.handleCallback(any()))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.FORWARDED))
			.thenReturn(new CallbackHandleResult(true, CallbackDecision.NOT_APPLICABLE))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.DEDUCTED));
		MockMvc mvc = mvc(callbackService, adapter);

		for (int index = 0; index < 3; index++) {
			mvc.perform(
					get("/renren-video/callback").param("kid", "v1").param("ext", "token").param("event", "activate"))
				.andExpect(status().isOk())
				.andExpect(content().string(""));
		}
	}

	@Test
	void returns400ForInvalidInputAnd500ForInternalFailure() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		RenrenVideoCallbackAdapter adapter = mock(RenrenVideoCallbackAdapter.class);
		when(adapter.toCallback(any())).thenThrow(new IllegalArgumentException("invalid"));
		MockMvc mvc = mvc(callbackService, adapter);

		mvc.perform(get("/renren-video/callback").param("event", "activate"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(""));

		reset(adapter);
		when(adapter.toCallback(any())).thenThrow(new IllegalStateException("internal"));
		mvc.perform(get("/renren-video/callback").param("kid", "v1").param("ext", "token").param("event", "activate"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string(""));
	}

	private MockMvc mvc(CallbackService callbackService, RenrenVideoCallbackAdapter adapter) {
		return MockMvcBuilders.standaloneSetup(new RenrenVideoCallbackController(callbackService, adapter)).build();
	}

}
