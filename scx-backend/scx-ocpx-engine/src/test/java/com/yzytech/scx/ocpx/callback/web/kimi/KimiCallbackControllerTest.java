package com.yzytech.scx.ocpx.callback.web.kimi;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.callback.service.CallbackService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KimiCallbackControllerTest {

	@Test
	void mergesQueryAndBodyParametersAndReturnsZeroCode() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		KimiCallbackAdapter adapter = mock(KimiCallbackAdapter.class);
		when(adapter.toCallback(any())).thenReturn(CallbackReq.builder().build());
		MockMvc mvc = mvc(callbackService, adapter);

		mvc.perform(post("/ocpx/kimi/callback").contextPath("/ocpx")
			.param("ext", "encrypted-ext")
			.param("s", "link-id_media-id")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"action_type\":\"activation\",\"action_time\":1700000000123}"))
			.andExpect(status().isOk())
			.andExpect(content().string("{\"code\":0}"));

		ArgumentCaptor<KimiCallbackReq> captor = ArgumentCaptor.forClass(KimiCallbackReq.class);
		verify(adapter).toCallback(captor.capture());
		KimiCallbackReq merged = captor.getValue();
		assertThat(merged.getExt()).isEqualTo("encrypted-ext");
		assertThat(merged.getS()).isEqualTo("link-id_media-id");
		assertThat(merged.getAction_type()).isEqualTo("activation");
		assertThat(merged.getAction_time()).isEqualTo(1700000000123L);
		verify(callbackService).handleCallback(any());
	}

	@Test
	void returnsFiveHundredAndOneCodeWhenTheCallbackCannotBeParsed() throws Exception {
		CallbackService callbackService = mock(CallbackService.class);
		KimiCallbackAdapter adapter = mock(KimiCallbackAdapter.class);
		when(adapter.toCallback(any())).thenThrow(new IllegalArgumentException("invalid ext"));
		MockMvc mvc = mvc(callbackService, adapter);

		mvc.perform(post("/ocpx/kimi/callback").contextPath("/ocpx")
			.param("ext", "invalid")
			.param("s", "link-id_media-id")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"action_type\":\"activation\",\"action_time\":1}"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string("{\"code\":1}"));
		verifyNoInteractions(callbackService);
	}

	private MockMvc mvc(CallbackService callbackService, KimiCallbackAdapter adapter) {
		return MockMvcBuilders.standaloneSetup(new KimiCallbackController(callbackService, adapter)).build();
	}

}
