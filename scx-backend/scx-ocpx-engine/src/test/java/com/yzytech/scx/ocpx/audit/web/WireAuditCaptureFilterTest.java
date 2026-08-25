package com.yzytech.scx.ocpx.audit.web;

import com.yzytech.scx.ocpx.audit.service.WireAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WireAuditCaptureFilterTest {

	@Test
	@SuppressWarnings("unchecked")
	void capturesDuplicateRawQueryBinaryBodiesAndMultiValueHeadersEvenWhenHandlerDoesNotReadBody() throws Exception {
		WireAuditService auditService = mock(WireAuditService.class);
		when(auditService.enabled()).thenReturn(true);
		WireAuditService.Handle handle = mock(WireAuditService.Handle.class);
		when(auditService.beginInbound(anyString(), anyString(), anyString(), any(), any())).thenReturn(handle);
		WireAuditCaptureFilter filter = new WireAuditCaptureFilter(auditService);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ocpx/feedback");
		request.setQueryString("tag=first&tag=second&raw=%2B");
		request.addHeader("X-Multi", "one");
		request.addHeader("X-Multi", "two");
		byte[] requestBody = new byte[] { 0, 1, 2, -1 };
		request.setContent(requestBody);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (wrappedRequest, wrappedResponse) -> {
			HttpServletResponse httpResponse = (HttpServletResponse) wrappedResponse;
			httpResponse.setContentType("application/octet-stream");
			httpResponse.addHeader("X-Reply", "a");
			httpResponse.addHeader("X-Reply", "b");
			httpResponse.getOutputStream().write(new byte[] { 3, 4, -2 });
		};

		filter.doFilter(request, response, chain);

		ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
		verify(auditService).beginInbound(eq("MEDIA_INGRESS"), eq("MEDIA"), eq("FORMAL"), requestCaptor.capture(),
				any());
		Map<String, Object> captured = requestCaptor.getValue();
		assertThat(captured.get("rawQuery")).isEqualTo("tag=first&tag=second&raw=%2B");
		assertThat(captured.get("bodyBase64")).isEqualTo(Base64.getEncoder().encodeToString(requestBody));
		assertThat((Map<String, List<String>>) captured.get("headers")).containsEntry("X-Multi",
				List.of("one", "two"));
		ArgumentCaptor<Map<String, List<String>>> responseHeaders = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<byte[]> responseBody = ArgumentCaptor.forClass(byte[].class);
		verify(auditService).completeHttp(eq(handle), eq(200), responseHeaders.capture(), responseBody.capture(),
				eq("HTTP_ACCEPTED"), isNull(), isNull());
		assertThat(responseHeaders.getValue()).containsEntry("X-Reply", List.of("a", "b"));
		assertThat(responseBody.getValue()).containsExactly(3, 4, -2);
		assertThat(response.getContentAsByteArray()).containsExactly(3, 4, -2);
	}

}
