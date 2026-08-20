package com.yzytech.scx.ocpx.feedback.web;

import com.yzytech.scx.ocpx.feedback.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackIngressControllerV119Test {

	@Test
	void rejectsMissingPlaceholderAndOversizedClickRequestIds() {
		FeedbackService service = mock(FeedbackService.class);
		FeedbackRequestAdapter adapter = mock(FeedbackRequestAdapter.class);
		FeedbackIngressController controller = new FeedbackIngressController(service, adapter);
		HttpServletRequest servlet = mock(HttpServletRequest.class);
		when(servlet.getParameterMap()).thenReturn(Map.of());
		for (String value : new String[] { null, "__REQUEST_ID__", "x".repeat(129) }) {
			FeedbackIngressRequest request = route("CLK", value);
			assertThat(controller.handleFeedback(request, servlet).getStatusCode().value()).isEqualTo(400);
		}
		verify(adapter, never()).toFeedback(org.mockito.ArgumentMatchers.any());
		verify(service, never()).handleFeedback(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void impressionKeepsCompatibilityFallback() {
		FeedbackService service = mock(FeedbackService.class);
		FeedbackIngressAdapter adapter = new FeedbackIngressAdapter();
		FeedbackIngressController controller = new FeedbackIngressController(service, adapter);
		HttpServletRequest servlet = mock(HttpServletRequest.class);
		when(servlet.getParameterMap()).thenReturn(Map.of());
		assertThat(controller.handleFeedback(route("IMP", "__REQUEST_ID__"), servlet).getStatusCode().value())
			.isEqualTo(200);
		verify(service).handleFeedback(org.mockito.ArgumentMatchers.argThat(value -> value.getReqId() != null));
	}

	private FeedbackIngressRequest route(String event, String requestId) {
		FeedbackIngressRequest request = new FeedbackIngressRequest();
		request.setEvent(event);
		request.setMId("media-id");
		request.setTId("task-id");
		request.setAId("advertiser-id");
		request.setLId("link-id");
		request.setRequest_id(requestId);
		return request;
	}

}
