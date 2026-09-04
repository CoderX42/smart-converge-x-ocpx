package com.yzytech.scx.ocpx.rta.admission;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.report.service.OcpxFlowLogWriter;
import com.yzytech.scx.ocpx.rta.exception.RtaAdmissionDeniedException;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



class RtaGateTest {

	@Test
	void disabledTaskBypassesAdmissionLookup() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RtaAdmissionStore admission = mock(RtaAdmissionStore.class);
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(false));

		new RtaGate(jdbc, admission, mock(OcpxFlowLogWriter.class)).requireAllowed(click());

		verify(admission, never()).requireAllowed(any());
	}

	@Test
	void enabledTaskFailsClosedWhenAdmissionIsMissing() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RtaAdmissionStore admission = mock(RtaAdmissionStore.class);
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(true));
		doThrow(new RtaAdmissionDeniedException("RTA_ADMISSION_MISSING")).when(admission).requireAllowed(any());

		assertThatThrownBy(() -> new RtaGate(jdbc, admission, mock(OcpxFlowLogWriter.class)).requireAllowed(click()))
			.isInstanceOf(RtaAdmissionDeniedException.class).hasMessage("RTA_ADMISSION_MISSING");
	}

	private FeedbackReq click() {
		return FeedbackReq.builder().mId("media-1").tId("task-1").aId("adv-1").lId("link-1")
				.reqId("req-1").event(EventTypeEnum.CLICK).build();
	}

}
