package com.yzytech.scx.common.security.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yzytech.scx.common.security.event.BaseSecurityAuditEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;

class BaseSecurityAuditListenerTest {

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void publishesSanitizedPermissionDenialEvent() {
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/boot/ad/channel-clients");
		request.addParameter("mediaId", "media_other");
		request.addParameter("token", "must-not-be-recorded");

		new BaseSecurityAuditListener(publisher).record(request, "NOT_PERMISSION");

		ArgumentCaptor<BaseSecurityAuditEvent> eventCaptor = ArgumentCaptor.forClass(BaseSecurityAuditEvent.class);
		verify(publisher).publishEvent(eventCaptor.capture());
		BaseSecurityAuditEvent event = eventCaptor.getValue();
		assertThat(event.getReasonCode()).isEqualTo("NOT_PERMISSION");
		assertThat(event.getRequestMethod()).isEqualTo("POST");
		assertThat(event.getRequestUri()).isEqualTo("/boot/ad/channel-clients");
		assertThat(event.getRequestParams()).isEqualTo("params=[mediaId,token]");
		assertThat(event.getRequestParams()).doesNotContain("media_other", "must-not-be-recorded");
		assertThat(event.getUserName()).isEqualTo("anonymous");
	}

}
