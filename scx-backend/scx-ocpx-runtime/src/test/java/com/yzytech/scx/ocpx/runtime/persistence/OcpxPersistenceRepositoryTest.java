package com.yzytech.scx.ocpx.runtime.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.BillingPort;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.model.FeedbackEnvelope;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcpxPersistenceRepositoryTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final ProtocolCrypto protocolCrypto = mock(ProtocolCrypto.class);

	private final BillingPort billingPort = mock(BillingPort.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	private OcpxPersistenceRepository repository;

	@BeforeEach
	void setUp() throws Exception {
		OcpxRuntimeProperties properties = new OcpxRuntimeProperties();
		repository = new OcpxPersistenceRepository(jdbcTemplate, objectMapper, protocolCrypto, properties, billingPort);
		when(protocolCrypto.decrypt("encrypted-route")).thenReturn(objectMapper.writeValueAsString(route()));
		when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);
	}

	@Test
	void insufficientBalancePersistsRejectedFactWithoutCreatingUpstreamDelivery() {
		when(billingPort.charge("click-1", "advertiser-1", new BigDecimal("1.250000"), "CNY", "CPC"))
			.thenReturn(new BillingPort.BillingResult("REJECTED", "INSUFFICIENT_BALANCE", new BigDecimal("0.500000")));

		repository.persistFeedbackBatch(List.of(envelope()));

		verify(jdbcTemplate).update(contains("BILLING_REJECTED"), eq("click-1"));
		verify(jdbcTemplate, never()).update(contains("INSERT IGNORE INTO scx_ocpx_delivery_job"), any(Object[].class));
	}

	@Test
	void postedCpcCreatesUpstreamDeliveryAndCostMetric() {
		when(billingPort.charge("click-1", "advertiser-1", new BigDecimal("1.250000"), "CNY", "CPC"))
			.thenReturn(new BillingPort.BillingResult("POSTED", "CLICK_CHARGED", new BigDecimal("8.750000")));

		repository.persistFeedbackBatch(List.of(envelope()));

		verify(jdbcTemplate).update(contains("INSERT IGNORE INTO scx_ocpx_delivery_job"), any(Object[].class));
		verify(jdbcTemplate).update(contains("cost_amount=cost_amount+VALUES(cost_amount)"), any(Object[].class));
	}

	@Test
	void callbackEventInsertHasOneValueForEverySqlPlaceholder() {
		OffsetDateTime now = OffsetDateTime.parse("2026-08-08T07:14:20Z");
		NormalizedCallbackEvent callback = new NormalizedCallbackEvent("callback-1", "click-1", "callback-ref",
				OcpxEventType.ACTIVATE, now, null, null);
		CallbackContext context = new CallbackContext("callback-ref", "click-1", route(), new FeedbackReqV1());

		repository.persistCallback(callback, context, false, "NO_RULE");

		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), argumentsCaptor.capture());
		int eventInsert = java.util.stream.IntStream.range(0, sqlCaptor.getAllValues().size())
			.filter(index -> sqlCaptor.getAllValues().get(index).contains("INSERT IGNORE INTO scx_ocpx_event"))
			.findFirst()
			.orElseThrow();
		long placeholders = sqlCaptor.getAllValues().get(eventInsert).chars().filter(character -> character == '?')
			.count();
		assertThat(argumentsCaptor.getAllValues().get(eventInsert)).hasSize((int) placeholders);
	}

	private FeedbackEnvelope envelope() {
		OffsetDateTime now = OffsetDateTime.parse("2026-08-07T09:00:00Z");
		return new FeedbackEnvelope("click-1", "payload-hash", OcpxEventType.CLICK, "channel", "monitor-1", 1, now, now,
				"device-hash", "callback-ref", "encrypted-feedback", "encrypted-route", true);
	}

	private RouteSnapshot route() {
		return new RouteSnapshot("monitor-1", "biz-1", 1, "channel-1", "channel", "STANDARD", "HMAC", 1,
				"channel-secret", "advertiser-1", "app-1", "TEMPLATE_GET", null, "https://upstream.example/feedback",
				null, "https://downstream.example/callback", new BigDecimal("1.250000"), BigDecimal.ONE, "OFF", "CPC");
	}

}
