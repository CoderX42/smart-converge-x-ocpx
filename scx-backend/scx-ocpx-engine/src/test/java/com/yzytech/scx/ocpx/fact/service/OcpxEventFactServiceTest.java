package com.yzytech.scx.ocpx.fact.service;

import com.yzytech.scx.ocpx.callback.model.CallbackReq;
import com.yzytech.scx.ocpx.feedback.model.FeedbackReq;
import com.yzytech.scx.ocpx.shared.enums.ActionTypeEnum;
import com.yzytech.scx.ocpx.shared.enums.EventTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxEventFactServiceTest {

	@Test
	void firstClickLocksAndChargesTheAccountBeforeProjectingSpend() throws Exception {
		Fixture fixture = fixture("2.500000", "ACTIVATE", "100.000000");

		var result = fixture.service.recordFeedback(feedback(EventTypeEnum.CLICK));

		assertThat(result.firstEvent()).isTrue();
		assertThat(result.forwardAllowed()).isTrue();
		assertThat(result.billingStatus()).isEqualTo("POSTED");
		assertThat(result.spendAmount()).isEqualByComparingTo("2.500000");
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(fixture.jdbcTemplate, atLeastOnce()).update(sql.capture(), args.capture());
		assertThat(sql.getAllValues()).anyMatch(value -> value.contains("UPDATE scx_ocpx_advertiser_account"));
		int accountUpdate = indexContaining(sql.getAllValues(), "UPDATE scx_ocpx_advertiser_account");
		assertThat((BigDecimal) args.getAllValues().get(accountUpdate)[0]).isEqualByComparingTo("97.500000");
		assertThat(sql.getAllValues()).anyMatch(value -> value.contains("INSERT INTO scx_ocpx_billing_entry"));
		assertThat(sql.getAllValues()).anyMatch(value -> value.contains("INSERT INTO scx_ocpx_hourly_metric"));
	}

	@Test
	void insufficientBalanceCountsTheClickWithoutSpendOrForwarding() throws Exception {
		Fixture fixture = fixture("2.500000", "ACTIVATE", "1.000000");

		var result = fixture.service.recordFeedback(feedback(EventTypeEnum.CLICK));

		assertThat(result.firstEvent()).isTrue();
		assertThat(result.forwardAllowed()).isFalse();
		assertThat(result.billingStatus()).isEqualTo("REJECTED_INSUFFICIENT_BALANCE");
		assertThat(result.spendAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(fixture.jdbcTemplate, atLeastOnce()).update(sql.capture(), any(Object[].class));
		assertThat(sql.getAllValues()).noneMatch(value -> value.contains("UPDATE scx_ocpx_advertiser_account"));
		assertThat(sql.getAllValues()).anyMatch(value -> value.contains("INSERT INTO scx_ocpx_billing_entry"));
		assertThat(sql.getAllValues()).anyMatch(value -> value.contains("INSERT INTO scx_ocpx_hourly_metric"));
	}

	@Test
	void duplicateUniqueFactDoesNotChargeOrAggregateAgain() throws Exception {
		Fixture fixture = fixture("2.500000", "ACTIVATE", "100.000000");
		when(fixture.jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_event_fact"),
				any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));

		var result = fixture.service.recordFeedback(feedback(EventTypeEnum.CLICK));

		assertThat(result.firstEvent()).isFalse();
		assertThat(result.forwardAllowed()).isFalse();
		verify(fixture.jdbcTemplate, never()).update(
				org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_billing_entry"), any(Object[].class));
		verify(fixture.jdbcTemplate, never()).update(
				org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_hourly_metric"), any(Object[].class));
	}

	@Test
	void callbackFreezesWhetherTheReceivedActionIsTheConfiguredTarget() throws Exception {
		Fixture fixture = fixture("2.500000", "PAYMENT", "100.000000");
		CallbackReq callback = CallbackReq.builder()
			.feedback(feedback(EventTypeEnum.CLICK))
			.actionType(ActionTypeEnum.PAYMENT)
			.currencyAmount(new BigDecimal("12.340000"))
			.build();

		var result = fixture.service.recordCallback(callback);

		assertThat(result.firstEvent()).isTrue();
		assertThat(result.targetConversion()).isTrue();
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(fixture.jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_event_fact"),
				args.capture());
		assertThat(args.getValue()[9]).isEqualTo(true);
	}

	@Test
	void disabledZeroPriceClickIsForwardedWithoutCharging() throws Exception {
		Fixture fixture = fixture("0.000000", "ACTIVATE", "0.000000", false, 1);

		var result = fixture.service.recordFeedback(feedback(EventTypeEnum.CLICK));

		assertThat(result.billingStatus()).isEqualTo("NOT_APPLICABLE");
		assertThat(result.forwardAllowed()).isTrue();
		assertThat(result.spendAmount()).isEqualByComparingTo("0.000000");
		verify(fixture.jdbcTemplate, never()).query(
				org.mockito.ArgumentMatchers.contains("FROM scx_ocpx_advertiser_account"), any(RowMapper.class),
				any(Object[].class));
	}

	@Test
	void conversionBillingForwardsClickWithoutCharging() throws Exception {
		Fixture fixture = fixture("3.000000", "ACTIVATE", "100.000000", true, 2);

		var result = fixture.service.recordFeedback(feedback(EventTypeEnum.CLICK));

		assertThat(result.forwardAllowed()).isTrue();
		assertThat(result.billingStatus()).isEqualTo("NOT_APPLICABLE");
		verify(fixture.jdbcTemplate, never()).update(
				org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_billing_entry"), any(Object[].class));
	}

	@Test
	void targetConversionChargesOnceAndProjectsSpend() throws Exception {
		Fixture fixture = fixture("3.000000", "ACTIVATE", "100.000000", true, 2);
		CallbackReq callback = CallbackReq.builder()
			.feedback(feedback(EventTypeEnum.CLICK))
			.actionType(ActionTypeEnum.ACTIVATE)
			.build();

		var result = fixture.service.recordCallback(callback);

		assertThat(result.firstEvent()).isTrue();
		assertThat(result.targetConversion()).isTrue();
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(fixture.jdbcTemplate, atLeastOnce()).update(sql.capture(), args.capture());
		int ledger = indexContaining(sql.getAllValues(), "INSERT INTO scx_ocpx_billing_entry");
		assertThat(args.getAllValues().get(ledger)[4]).isEqualTo("CONVERSION_CHARGE");
		assertThat(args.getAllValues().get(ledger)[5]).isEqualTo("POSTED");
		assertThat(sql.getAllValues()).anyMatch(value -> value.contains("UPDATE scx_ocpx_advertiser_account"));
	}

	@Test
	void nonTargetConversionDoesNotCharge() throws Exception {
		Fixture fixture = fixture("3.000000", "PAYMENT", "100.000000", true, 2);
		CallbackReq callback = CallbackReq.builder()
			.feedback(feedback(EventTypeEnum.CLICK))
			.actionType(ActionTypeEnum.ACTIVATE)
			.build();

		var result = fixture.service.recordCallback(callback);

		assertThat(result.targetConversion()).isFalse();
		verify(fixture.jdbcTemplate, never()).update(
				org.mockito.ArgumentMatchers.contains("INSERT INTO scx_ocpx_billing_entry"), any(Object[].class));
	}

	@Test
	void insufficientConversionBalanceRejectsChargeButKeepsCallbackEligible() throws Exception {
		Fixture fixture = fixture("3.000000", "ACTIVATE", "1.000000", true, 2);
		CallbackReq callback = CallbackReq.builder()
			.feedback(feedback(EventTypeEnum.CLICK))
			.actionType(ActionTypeEnum.ACTIVATE)
			.build();

		var result = fixture.service.recordCallback(callback);

		assertThat(result.firstEvent()).isTrue();
		assertThat(result.targetConversion()).isTrue();
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(fixture.jdbcTemplate, atLeastOnce()).update(sql.capture(), args.capture());
		int ledger = indexContaining(sql.getAllValues(), "INSERT INTO scx_ocpx_billing_entry");
		assertThat(args.getAllValues().get(ledger)[4]).isEqualTo("CONVERSION_CHARGE");
		assertThat(args.getAllValues().get(ledger)[5]).isEqualTo("REJECTED");
		assertThat(sql.getAllValues()).noneMatch(value -> value.contains("UPDATE scx_ocpx_advertiser_account"));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Fixture fixture(String price, String conversionEvent, String balance) throws Exception {
		return fixture(price, conversionEvent, balance, true, 1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Fixture fixture(String price, String conversionEvent, String balance, boolean billingEnabled,
			int billingType) throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			RowMapper mapper = invocation.getArgument(1);
			ResultSet resultSet = mock(ResultSet.class);
			if (sql.contains("FROM scx_ocpx_monitor_task")) {
				when(resultSet.getString("id")).thenReturn("task-1");
				when(resultSet.getString("advertiser_id")).thenReturn("advertiser-1");
				when(resultSet.getString("promotion_link_id")).thenReturn("link-1");
				when(resultSet.getString("media_id")).thenReturn("media-1");
				when(resultSet.getBoolean("billing_enabled")).thenReturn(billingEnabled);
				when(resultSet.getInt("billing_type")).thenReturn(billingType);
				when(resultSet.getBigDecimal("unit_price")).thenReturn(new BigDecimal(price));
				when(resultSet.getString("conversion_event")).thenReturn(conversionEvent);
			}
			else {
				when(resultSet.getBigDecimal(1)).thenReturn(new BigDecimal(balance));
			}
			return List.of(mapper.mapRow(resultSet, 0));
		});
		return new Fixture(jdbcTemplate, new OcpxEventFactService(jdbcTemplate));
	}

	private FeedbackReq feedback(EventTypeEnum event) {
		return FeedbackReq.builder()
			.aId("advertiser-1")
			.tId("task-1")
			.lId("link-1")
			.mId("media-1")
			.reqId("request-1")
			.event(event)
			.build();
	}

	private int indexContaining(List<String> values, String expected) {
		for (int index = 0; index < values.size(); index++) {
			if (values.get(index).contains(expected)) {
				return index;
			}
		}
		throw new AssertionError("SQL not found: " + expected);
	}

	private record Fixture(JdbcTemplate jdbcTemplate, OcpxEventFactService service) {
	}

}
