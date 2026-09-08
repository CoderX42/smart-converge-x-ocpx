package com.yzytech.scx.ocpx.report.service;

import com.yzytech.scx.ocpx.persistence.mapper.OcpxFlowLogWriterSqlMapper;
import com.yzytech.scx.ocpx.testing.XmlMapperTestSupport;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class OcpxFlowLogWriterTest {

	@Test
	void rejectedAdvertiserAuditContainsOnlyRouteAndSanitizedMetadata() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		OcpxFlowLogWriter writer = new OcpxFlowLogWriter(
				XmlMapperTestSupport.mapper(OcpxFlowLogWriterSqlMapper.class, jdbcTemplate));
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

	@Test
	void upstreamResponsePreviewIsRedactedAndBounded() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		OcpxFlowLogWriter writer = new OcpxFlowLogWriter(
				XmlMapperTestSupport.mapper(OcpxFlowLogWriterSqlMapper.class, jdbcTemplate));
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.event(EventTypeEnum.CLICK)
			.build();
		String response = "{\"code\":402,\"message\":\"campaign paused\",\"token\":\"private-token\"}"
				+ "x".repeat(5000);

		writer.upstreamDelivery(feedback, false, 200, "UPSTREAM_REJECTED", 9L, response);

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, times(2)).update(anyString(), arguments.capture());
		Object[] persistedArguments = arguments.getAllValues().get(0);
		assertThat(persistedArguments[18].toString()).contains("\"code\":402", "campaign paused", "\"token\":\"***\"")
			.doesNotContain("private-token")
			.hasSize(4096);
		assertThat(persistedArguments[19]).isEqualTo(9L);
	}

	@Test
	void callbackAuditUsesCallbackTypeAndContainsOnlySanitizedMetadata() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		OcpxFlowLogWriter writer = new OcpxFlowLogWriter(
				XmlMapperTestSupport.mapper(OcpxFlowLogWriterSqlMapper.class, jdbcTemplate));
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.imei("sensitive-device-id")
			.callbackUrl("https%3A%2F%2Fcallback.example%2Fsecret")
			.ext("sensitive-extension")
			.build();
		CallbackReq callback = CallbackReq.builder()
			.feedback(feedback)
			.actionType(ActionTypeEnum.EVOKE)
			.actionTime(1L)
			.partnerActionType("engagement")
			.build();

		writer.callback(callback, true, "DOWNSTREAM_DELIVERY_SUBMITTED");

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(anyString(), arguments.capture());
		Object[] persistedArguments = arguments.getValue();
		assertThat(persistedArguments[2]).isEqualTo("CALLBACK");
		assertThat(persistedArguments[3]).isEqualTo("ADVERTISER_CALLBACK");
		assertThat(persistedArguments[4]).isEqualTo("request-id");
		assertThat(persistedArguments[6]).isEqualTo("EVOKE");
		assertThat(persistedArguments[15]).isEqualTo("SUCCESS");
		assertThat(persistedArguments[17]).isEqualTo("DOWNSTREAM_DELIVERY_SUBMITTED");
		assertThat(Arrays.toString(persistedArguments)).contains("advertiser-id", "link-id", "media-id", "task-id")
			.doesNotContain("sensitive-device-id", "callback.example", "sensitive-extension");
	}

	@Test
	void samplesSuccessfulAuditButAlwaysPersistsFailures() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		OcpxFlowLogWriter writer = new OcpxFlowLogWriter(
				XmlMapperTestSupport.mapper(OcpxFlowLogWriterSqlMapper.class, jdbcTemplate));
		ReflectionTestUtils.setField(writer, "successSampleRate", 0.0d);
		FeedbackReq feedback = FeedbackReq.builder().reqId("request-id").event(EventTypeEnum.CLICK).build();

		writer.mediaIngress(feedback, true, "OK");
		verify(jdbcTemplate, times(0)).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));

		writer.mediaIngress(feedback, false, "FAILED");
		verify(jdbcTemplate, times(2)).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));

		CallbackReq callback = CallbackReq.builder().feedback(feedback).actionType(ActionTypeEnum.EVOKE).build();
		writer.accounting(callback, false, "CALLBACK_ACCOUNTING_DUPLICATE");
		verify(jdbcTemplate, times(2)).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));

		writer.accounting(callback, false, "CALLBACK_ACCOUNTING_FAILED");
		verify(jdbcTemplate, times(3)).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
	}

	@Test
	void rtaProtectedMediaDetailOmitsRawDeviceIdentifiers() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		OcpxFlowLogWriter writer = new OcpxFlowLogWriter(
				XmlMapperTestSupport.mapper(OcpxFlowLogWriterSqlMapper.class, jdbcTemplate));
		FeedbackReq feedback = FeedbackReq.builder()
			.mId("media-id")
			.tId("task-id")
			.aId("advertiser-id")
			.lId("link-id")
			.reqId("request-id")
			.event(EventTypeEnum.IMP)
			.imei("rta-raw-imei")
			.idfa("rta-raw-idfa")
			.androidid("rta-raw-android-id")
			.mac("rta-raw-mac")
			.rtaProtected(true)
			.build();

		writer.mediaIngress(feedback, true, "MEDIA_INGRESS_ACCEPTED");

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, times(2)).update(anyString(), arguments.capture());
		assertThat(arguments.getAllValues().toString()).doesNotContain("rta-raw-imei", "rta-raw-idfa",
				"rta-raw-android-id", "rta-raw-mac");
	}

}
