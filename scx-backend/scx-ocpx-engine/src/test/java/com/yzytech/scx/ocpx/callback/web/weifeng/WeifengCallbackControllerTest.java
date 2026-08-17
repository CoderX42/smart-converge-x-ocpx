package com.yzytech.scx.ocpx.callback.web.weifeng;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackDecision;
import com.yzytech.scx.ocpx.callback.service.CallbackHandleResult;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WeifengCallbackControllerTest {

	private final CallbackService callbackService = mock(CallbackService.class);

	private final WeifengCallbackAdapter callbackAdapter = mock(WeifengCallbackAdapter.class);

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new WeifengCallbackController(callbackService, callbackAdapter)).build();
	}

	@Test
	void returnsOkForAcceptedCallback() throws Exception {
		when(callbackAdapter.toCallback(any())).thenReturn(new CallbackReq());
		when(callbackService.handleCallback(any()))
			.thenReturn(new CallbackHandleResult(false, CallbackDecision.FORWARDED));

		mvc.perform(get("/weifeng/callback").param("ext", "opaque")
			.param("s", "l_t")
			.param("event", "PAID")
			.param("amount", "12.30")
			.param("days", "__DAYS__")).andExpect(status().isOk());
	}

	@Test
	void returnsBadRequestForRejectedProtocolParameters() throws Exception {
		when(callbackAdapter.toCallback(any())).thenThrow(new IllegalArgumentException("invalid"));

		mvc.perform(get("/weifeng/callback").param("event", "UNKNOWN")).andExpect(status().isBadRequest());
	}

	@Test
	void returnsServerErrorForHandlingFailure() throws Exception {
		when(callbackAdapter.toCallback(any())).thenReturn(new CallbackReq());
		when(callbackService.handleCallback(any())).thenThrow(new IllegalStateException("database unavailable"));

		mvc.perform(get("/weifeng/callback").param("event", "PAID")).andExpect(status().isInternalServerError());
	}

}
