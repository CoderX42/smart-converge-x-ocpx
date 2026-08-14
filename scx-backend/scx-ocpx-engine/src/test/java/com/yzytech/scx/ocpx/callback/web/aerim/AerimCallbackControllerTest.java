package com.yzytech.scx.ocpx.callback.web.aerim;

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

class AerimCallbackControllerTest {

	@Test
	void returnsEmpty200ForForwardedDuplicateAndDeductedResults() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		AerimCallbackAdapter adapter = mock(AerimCallbackAdapter.class);
		when(adapter.toCallback(any())).thenReturn(new CallbackReq());
		when(callbackService.handleCallback(any()))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.FORWARDED))
			.thenReturn(new CallbackHandleResult(true, CallbackDecision.NOT_APPLICABLE))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.DEDUCTED));
		MockMvc mvc = mvc(callbackService, adapter);

		for (int index = 0; index < 3; index++) {
			mvc.perform(
					get("/aerim/callback").param("ext", "cipher").param("s", "state").param("action_type", "activate"))
				.andExpect(status().isOk())
				.andExpect(content().string(""));
		}
	}

	@Test
	void returns400ForProtocolErrorsAndMalformedDecimalAnd500ForInternalErrors() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		AerimCallbackAdapter adapter = mock(AerimCallbackAdapter.class);
		when(adapter.toCallback(any())).thenThrow(new IllegalArgumentException("bad request"));
		MockMvc mvc = mvc(callbackService, adapter);
		mvc.perform(get("/aerim/callback").param("ext", "cipher").param("s", "state").param("action_type", "active"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(""));
		mvc.perform(get("/aerim/callback").param("ext", "cipher")
			.param("s", "state")
			.param("action_type", "purchase")
			.param("currency_amount", "not-a-number")).andExpect(status().isBadRequest());

		reset(adapter);
		when(adapter.toCallback(any())).thenThrow(new IllegalStateException("internal"));
		mvc.perform(get("/aerim/callback").param("ext", "cipher").param("s", "state").param("action_type", "activate"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string(""));
	}

	private MockMvc mvc(CallbackService callbackService, AerimCallbackAdapter adapter) {
		return MockMvcBuilders.standaloneSetup(new AerimCallbackController(callbackService, adapter)).build();
	}

}
