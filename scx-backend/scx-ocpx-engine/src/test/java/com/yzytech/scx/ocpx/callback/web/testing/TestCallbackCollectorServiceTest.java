package com.yzytech.scx.ocpx.callback.web.testing;

import com.yzytech.scx.ocpx.callback.service.TestCallbackCollectorService;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestCallbackCollectorServiceTest {

	private JdbcTemplate jdbcTemplate;

	private TestCallbackCollectorService service;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		service = new TestCallbackCollectorService(jdbcTemplate, "https://tracking.example/ocpx", "test-signing-key");
	}

	@Test
	void armsOnlyTheFixedCollectorRouteFromTheOriginalFeedback() {
		String requestId = "SCX-UAT-20260818120000-abcdef123456";
		FeedbackReq request = FeedbackReq.builder()
			.reqId(requestId)
			.mId("media-1")
			.tId("task-1")
			.aId("adv-1")
			.lId("link-1")
			.event(EventTypeEnum.CLICK)
			.callbackUrl("https://tracking.example/ocpx/test-callback?request_id=" + requestId
					+ "&token=0123456789abcdef0123456789abcdef&mId=media-1&tId=task-1&aId=adv-1&lId=link-1")
			.build();

		assertThat(service.arm(request)).isTrue();
		verify(jdbcTemplate).update(contains("TEST_CALLBACK_ARMED"), any(Object[].class));
	}

	@Test
	void recordsOnlyAfterTheMatchingCallbackPassedMediaDeduction() {
		String requestId = "SCX-UAT-20260818120000-abcdef123456";
		when(jdbcTemplate.queryForObject(contains("message='TEST_CALLBACK_ARMED'"), eq(Long.class), eq(requestId),
				eq("media-1"), eq("task-1"), eq("adv-1"), eq("link-1"), org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(1L);
		when(jdbcTemplate.queryForObject(contains("deduction_decision='PASSED'"), eq(Long.class), eq(requestId),
				eq("media-1"), eq("task-1"), eq("adv-1"), eq("link-1"), eq("ACTIVATE")))
			.thenReturn(1L);

		boolean inserted = service.record(requestId, "0123456789abcdef0123456789abcdef", "media-1", "task-1", "adv-1",
				"link-1", 1, new BigDecimal("1.250000"), signature(requestId, new BigDecimal("1.250000")));

		assertThat(inserted).isTrue();
		verify(jdbcTemplate).update(contains("TEST_CALLBACK_RECEIVED"), any(Object[].class));
	}

	@Test
	void rejectsForgedOrPrematureCollectorRequests() {
		String requestId = "SCX-UAT-20260818120000-abcdef123456";
		when(jdbcTemplate.queryForObject(contains("message='TEST_CALLBACK_ARMED'"), eq(Long.class), eq(requestId),
				eq("media-1"), eq("task-1"), eq("adv-1"), eq("link-1"), org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(1L);
		when(jdbcTemplate.queryForObject(contains("deduction_decision='PASSED'"), eq(Long.class), eq(requestId),
				eq("media-1"), eq("task-1"), eq("adv-1"), eq("link-1"), eq("ACTIVATE")))
			.thenReturn(0L);

		assertThatThrownBy(() -> service.record(requestId, "0123456789abcdef0123456789abcdef", "media-1", "task-1",
				"adv-1", "link-1", 1, null, signature(requestId, null)))
			.isInstanceOf(TestCallbackCollectorService.CallbackNotReadyException.class);
		verify(jdbcTemplate, never()).update(contains("INSERT INTO scx_ocpx_flow_log"), any(Object[].class));

		assertThatThrownBy(() -> service.record(requestId, "bad-token", "media-1", "task-1", "adv-1", "link-1", 1, null,
				"0".repeat(64)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.record(requestId, "0123456789abcdef0123456789abcdef", "media-1", "task-1",
				"adv-1", "link-1", 1, null, "0".repeat(64)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("signature");
	}

	private String signature(String requestId, BigDecimal amount) {
		String callbackUrl = "https://tracking.example/ocpx/test-callback?request_id=" + requestId
				+ "&token=0123456789abcdef0123456789abcdef&mId=media-1&tId=task-1&aId=adv-1&lId=link-1"
				+ "&event_type=1" + (amount == null ? "" : "&currency_amount=" + amount.toPlainString());
		String signed = service.signDeliveryIfTestCallback(callbackUrl, 1, amount);
		return signed.substring(signed.indexOf("delivery_signature=") + "delivery_signature=".length());
	}

}
