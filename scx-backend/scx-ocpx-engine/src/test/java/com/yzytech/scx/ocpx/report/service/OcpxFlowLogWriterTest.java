package com.yzytech.scx.ocpx.report.service;

import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OcpxFlowLogWriterTest {

	@Test
	void rejectedAdvertiserAuditContainsOnlyRouteAndSanitizedMetadata() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		OcpxFlowLogWriter writer = new OcpxFlowLogWriter(jdbcTemplate);
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.event(EventTypeEnum.CLICK)
			.imei("sensitive-device-id")
			.callbackUrl("https%3A%2F%2Fcallback.example%2Fsecret")
			.ext("sensitive-extension")
			.build();

		writer.rejectedAdvertiser(feedback);

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(anyString(), arguments.capture());
		String persisted = Arrays.toString(arguments.getValue());
		assertThat(persisted)
			.contains("advertiser-id", "link-id", "media-id", "task-id", "ADVERTISER_PROTOCOL_NOT_REGISTERED")
			.doesNotContain("sensitive-device-id", "callback.example", "sensitive-extension");
	}

}
