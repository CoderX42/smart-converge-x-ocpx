package com.yzytech.scx.common.log.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yzytech.scx.common.security.event.BaseSecurityAuditEvent;
import com.yzytech.scx.system.api.dto.SysLogReqDTO;
import com.yzytech.scx.system.api.remote.RemoteSysLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BootLogEventListenerTest {

	@Test
	void persistsSecurityDenialAsASystemLog() {
		RemoteSysLogService remoteSysLogService = mock(RemoteSysLogService.class);
		BaseSecurityAuditEvent event = new BaseSecurityAuditEvent(this, "NOT_ROLE", "POST", "/boot/ad/channel-clients",
				"params=[mediaId]", "127.0.0.1", "operator");

		new BootLogEventListener(remoteSysLogService).saveSecurityDenialLog(event);

		ArgumentCaptor<SysLogReqDTO> logCaptor = ArgumentCaptor.forClass(SysLogReqDTO.class);
		verify(remoteSysLogService).saveLog(logCaptor.capture());
		SysLogReqDTO sysLog = logCaptor.getValue();
		assertThat(sysLog.getTitle()).isEqualTo("无权限访问");
		assertThat(sysLog.getStatus()).isEqualTo("0");
		assertThat(sysLog.getMethod()).isEqualTo("POST");
		assertThat(sysLog.getRequestMethod()).isEqualTo("SECURITY_DENIAL:NOT_ROLE");
		assertThat(sysLog.getExMsg()).isEqualTo("NOT_ROLE");
		assertThat(sysLog.getRequestUri()).isEqualTo("/boot/ad/channel-clients");
		assertThat(sysLog.getRequestParams()).isEqualTo("params=[mediaId]");
		assertThat(sysLog.getCreateBy()).isEqualTo("system");
	}

}
