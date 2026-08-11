package com.yzytech.scx.ocpx.runtime.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzytech.scx.ocpx.contract.BillingPort;
import com.yzytech.scx.ocpx.contract.FeedbackReqV1;
import com.yzytech.scx.ocpx.contract.NormalizedCallbackEvent;
import com.yzytech.scx.ocpx.contract.OcpxEventType;
import com.yzytech.scx.ocpx.contract.RouteEventSnapshot;
import com.yzytech.scx.ocpx.contract.RouteSnapshot;
import com.yzytech.scx.ocpx.runtime.config.OcpxRuntimeProperties;
import com.yzytech.scx.ocpx.runtime.model.CallbackContext;
import com.yzytech.scx.ocpx.runtime.model.DeliveryJob;
import com.yzytech.scx.ocpx.runtime.model.FeedbackEnvelope;
import com.yzytech.scx.ocpx.runtime.security.ProtocolCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
		when(protocolCrypto.decrypt("encrypted-route")).thenReturn(objectMapper.writeValueAsString(frozenRoute()));
		when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);
		when(billingPort.charge(any(), any(), any(), eq("CNY"), eq("CPA")))
			.thenReturn(new BillingPort.BillingResult("POSTED", "FREE_EVENT", BigDecimal.TEN));
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
		ArgumentCaptor<Object[]> contribution = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(contains("INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution"),
				contribution.capture());
		assertThat(contribution.getValue()[19]).isEqualTo(1L);
		assertThat(contribution.getValue()[20]).isEqualTo(0L);
		assertThat(contribution.getValue()[22]).isEqualTo(new BigDecimal("1.250000"));
	}

	@Test
	void deviceWindowDuplicateKeepsFactButSkipsBillingAndDelivery() throws Exception {
		when(jdbcTemplate.update(contains("INSERT IGNORE INTO scx_ocpx_click_dedup_state"), any(Object[].class)))
			.thenReturn(0);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString(1)).thenReturn("canonical-click");
		when(resultSet.getTimestamp(2))
			.thenReturn(Timestamp.from(OffsetDateTime.parse("2026-08-07T08:59:30Z").toInstant()));
		when(jdbcTemplate.queryForObject(contains("FROM scx_ocpx_click_dedup_state"),
				any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
			.thenAnswer(invocation -> {
				org.springframework.jdbc.core.RowMapper<?> mapper = invocation.getArgument(1);
				return mapper.mapRow(resultSet, 0);
			});

		repository.persistFeedbackBatch(List.of(envelope()));

		verify(jdbcTemplate).update(contains("status='DEDUPLICATED'"), eq("click-1"));
		verify(billingPort, never()).charge(any(), any(), any(), any(), any());
		verify(jdbcTemplate, never()).update(contains("INSERT IGNORE INTO scx_ocpx_delivery_job"), any(Object[].class));
		ArgumentCaptor<Object[]> contribution = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(contains("INSERT IGNORE INTO scx_ocpx_hourly_fact_contribution"),
				contribution.capture());
		assertThat(contribution.getValue()[18]).isEqualTo(1);
		assertThat(contribution.getValue()[19]).isEqualTo(0L);
		assertThat(contribution.getValue()[20]).isEqualTo(1L);
		assertThat(contribution.getValue()[21]).isEqualTo(0L);
		assertThat(contribution.getValue()[22]).isEqualTo(BigDecimal.ZERO);
	}

	@Test
	void callbackEventInsertHasOneValueForEverySqlPlaceholder() {
		OffsetDateTime now = OffsetDateTime.parse("2026-08-08T07:14:20Z");
		NormalizedCallbackEvent callback = new NormalizedCallbackEvent("callback-1", "click-1", "callback-ref",
				OcpxEventType.ACTIVATE, now, null, null);
		CallbackContext context = new CallbackContext("callback-ref", "click-1", frozenRoute(), new FeedbackReqV1());

		repository.persistCallback(callback, context, false, "NO_RULE");

		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), argumentsCaptor.capture());
		int eventInsert = java.util.stream.IntStream.range(0, sqlCaptor.getAllValues().size())
			.filter(index -> sqlCaptor.getAllValues().get(index).contains("INSERT IGNORE INTO scx_ocpx_event"))
			.findFirst()
			.orElseThrow();
		long placeholders = sqlCaptor.getAllValues()
			.get(eventInsert)
			.chars()
			.filter(character -> character == '?')
			.count();
		assertThat(argumentsCaptor.getAllValues().get(eventInsert)).hasSize((int) placeholders);
	}

	@Test
	void runtimeFactProjectionHasOneValueForEverySqlPlaceholder() {
		when(billingPort.charge("click-1", "advertiser-1", new BigDecimal("1.250000"), "CNY", "CPC"))
			.thenReturn(new BillingPort.BillingResult("POSTED", "CLICK_CHARGED", new BigDecimal("8.750000")));
		repository.persistFeedbackBatch(List.of(envelope()));
		OffsetDateTime now = OffsetDateTime.parse("2026-08-08T07:14:20Z");
		repository.persistCallback(
				new NormalizedCallbackEvent("callback-1", "click-1", "callback-ref", OcpxEventType.ACTIVATE, now, null,
						null),
				new CallbackContext("callback-ref", "click-1", frozenRoute(), new FeedbackReqV1()), false, "NO_RULE");

		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), argumentsCaptor.capture());
		List<Integer> runtimeFactInserts = java.util.stream.IntStream.range(0, sqlCaptor.getAllValues().size())
			.filter(index -> sqlCaptor.getAllValues().get(index).contains("INSERT IGNORE INTO scx_ocpx_runtime_fact"))
			.boxed()
			.toList();
		assertThat(runtimeFactInserts).isNotEmpty();
		for (Integer index : runtimeFactInserts) {
			long placeholders = sqlCaptor.getAllValues()
				.get(index)
				.chars()
				.filter(character -> character == '?')
				.count();
			assertThat(argumentsCaptor.getAllValues().get(index)).hasSize((int) placeholders);
		}
	}

	@Test
	void callbackMetricsKeepFirstActivationReactivationAndRetentionDaysIndependent() {
		OffsetDateTime now = OffsetDateTime.parse("2026-08-08T07:14:20Z");
		CallbackContext context = new CallbackContext("callback-ref", "click-1", frozenRoute(), new FeedbackReqV1());

		repository.persistCallback(new NormalizedCallbackEvent("first-activate-1", "click-1", "callback-ref",
				OcpxEventType.FIRST_ACTIVATION, now, null, null), context, true, "NO_RULE");
		repository.persistCallback(new NormalizedCallbackEvent("reactivation-1", "click-1", "callback-ref",
				OcpxEventType.REACTIVATION, now, null, null), context, true, "NO_RULE");
		repository.persistCallback(new NormalizedCallbackEvent("retention-d7-1", "click-1", "callback-ref",
				OcpxEventType.RETENTION_D7, now, null, null), context, true, "NO_RULE");

		verify(jdbcTemplate).update(contains("first_activation_count"), any(Object[].class));
		verify(jdbcTemplate).update(contains("reactivation_count"), any(Object[].class));
		verify(jdbcTemplate).update(contains("retention_d7_count"), any(Object[].class));
	}

	@Test
	void callbackAggregationUsesShanghaiNaturalHour() {
		OffsetDateTime actionTime = OffsetDateTime.parse("2026-08-08T17:30:00Z");
		CallbackContext context = new CallbackContext("callback-ref", "click-1", frozenRoute(), new FeedbackReqV1());

		repository.persistCallback(new NormalizedCallbackEvent("activate-boundary", "click-1", "callback-ref",
				OcpxEventType.ACTIVATE, actionTime, null, null), context, true, "NO_RULE");

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(contains("activation_count"), arguments.capture());
		assertThat(arguments.getValue()[16]).isEqualTo(java.time.LocalDateTime.parse("2026-08-09T01:00:00"));
	}

	@Test
	void debugCallbackNeverChangesDashboardFacts() {
		OffsetDateTime actionTime = OffsetDateTime.parse("2026-08-08T17:30:00Z");
		RouteEventSnapshot eventMapping = eventMapping(10);
		CallbackContext context = new CallbackContext("callback-ref", "click-1", versionedRoute(eventMapping),
				new FeedbackReqV1(), "debug-execution-1");

		repository.persistCallback(
				new NormalizedCallbackEvent("debug-activate", "click-1", "callback-ref", OcpxEventType.ACTIVATE,
						actionTime, null, null)
					.mapped("click-1", OcpxEventType.ACTIVATE, eventMapping),
				context, true, "NO_RULE");

		verify(jdbcTemplate, never()).update(contains("INSERT INTO scx_ocpx_hourly_report"), any(Object[].class));
		verify(jdbcTemplate, never()).update(contains("scx_ocpx_event_daily_quota"), any(Object[].class));
		verify(billingPort, never()).charge(any(), any(), any(), eq("CNY"), eq("CPA"));
	}

	@Test
	void publishedEventSnapshotChargesCpaOccupiesQuotaAndCreatesDeliveryAtomically() {
		when(jdbcTemplate.queryForObject(contains("SELECT queued_count"), eq(Integer.class), any(Object[].class)))
			.thenReturn(0);
		OffsetDateTime actionTime = OffsetDateTime.parse("2026-08-08T17:30:00Z");
		RouteEventSnapshot eventMapping = eventMapping(10);
		NormalizedCallbackEvent callback = new NormalizedCallbackEvent("mapped-activate", "click-1", "callback-ref",
				OcpxEventType.ACTIVATE, actionTime, null, null)
			.mapped("click-1", OcpxEventType.ACTIVATE, eventMapping);
		CallbackContext context = new CallbackContext("callback-ref", "click-1", versionedRoute(eventMapping),
				new FeedbackReqV1());

		OcpxPersistenceRepository.CallbackPersistenceResult result = repository.persistCallback(callback, context, true,
				"NO_RULE");

		assertThat(result.status()).isEqualTo("ACCEPTED");
		verify(billingPort).charge("mapped-activate", "advertiser-1", new BigDecimal("2.500000"), "CNY", "CPA");
		verify(jdbcTemplate).update(contains("queued_count=queued_count+1"), any(Object[].class));
		verify(jdbcTemplate).update(contains("INSERT INTO scx_ocpx_delivery_job"), any(Object[].class));
	}

	@Test
	void publishedEventSnapshotRejectsAtStrictDailyLimitBeforeBillingOrFacts() {
		when(jdbcTemplate.queryForObject(contains("SELECT queued_count"), eq(Integer.class), any(Object[].class)))
			.thenReturn(10);
		OffsetDateTime actionTime = OffsetDateTime.parse("2026-08-08T17:30:00Z");
		RouteEventSnapshot eventMapping = eventMapping(10);
		NormalizedCallbackEvent callback = new NormalizedCallbackEvent("limited-activate", "click-1", "callback-ref",
				OcpxEventType.ACTIVATE, actionTime, null, null)
			.mapped("click-1", OcpxEventType.ACTIVATE, eventMapping);
		CallbackContext context = new CallbackContext("callback-ref", "click-1", versionedRoute(eventMapping),
				new FeedbackReqV1());

		OcpxPersistenceRepository.CallbackPersistenceResult result = repository.persistCallback(callback, context, true,
				"NO_RULE");

		assertThat(result.status()).isEqualTo("LIMIT_EXCEEDED");
		verify(billingPort, never()).charge(any(), any(), any(), eq("CNY"), eq("CPA"));
		verify(jdbcTemplate, never()).update(contains("INSERT INTO scx_ocpx_delivery_job"), any(Object[].class));
	}

	@Test
	void legacyRouteSnapshotKeepsHistoricalCallbackBehaviorWithoutCpaOrQuota() {
		OffsetDateTime actionTime = OffsetDateTime.parse("2026-08-08T17:30:00Z");
		CallbackContext context = new CallbackContext("callback-ref", "click-1", route(), new FeedbackReqV1());

		OcpxPersistenceRepository.CallbackPersistenceResult result = repository
			.persistCallback(new NormalizedCallbackEvent("legacy-activate", "click-1", "callback-ref",
					OcpxEventType.ACTIVATE, actionTime, null, null), context, true, "NO_RULE");

		assertThat(result.status()).isEqualTo("ACCEPTED");
		verify(billingPort, never()).charge(any(), any(), any(), eq("CNY"), eq("CPA"));
		verify(jdbcTemplate, never()).update(contains("scx_ocpx_event_daily_quota"), any(Object[].class));
	}

	@Test
	void deadDownstreamDeliveryKeepsPostedCpaLedgerUntouched() {
		DeliveryJob job = new DeliveryJob("job-1", "callback-1", "click-1", "CALLBACK_DOWNSTREAM", "STANDARD",
				"callback-ref", 4, null, "lease-1");

		repository.markDeliveryFailure(job, 500, "downstream unavailable");

		verify(jdbcTemplate).update(contains("SET status=?"), eq("DEAD"), eq(5), eq(600L), eq("downstream unavailable"),
				eq("job-1"), eq("lease-1"), eq(4));
		verify(jdbcTemplate, never()).update(contains("scx_ocpx_billing_entry"), any(Object[].class));
		verify(billingPort, never()).charge(any(), any(), any(), any(), any());
	}

	@Test
	void deliveryClaimsUseUniqueFencingTokens() {
		DeliveryJob first = new DeliveryJob("job-1", "event-1", "source-1", "FEEDBACK_UPSTREAM", "STANDARD",
				"callback-1", 0);
		DeliveryJob second = new DeliveryJob("job-2", "event-2", "source-2", "FEEDBACK_UPSTREAM", "STANDARD",
				"callback-2", 2);
		when(jdbcTemplate.query(contains("FOR UPDATE SKIP LOCKED"),
				org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<DeliveryJob>>any(),
				eq("FEEDBACK_UPSTREAM"), eq(100)))
			.thenReturn(List.of(first, second));

		List<DeliveryJob> claimed = repository.claimDeliveryJobs(100, "FEEDBACK_UPSTREAM");

		assertThat(claimed).hasSize(2);
		assertThat(claimed).allSatisfy(job -> assertThat(job.leaseToken()).isNotBlank());
		assertThat(claimed.get(0).leaseToken()).isNotEqualTo(claimed.get(1).leaseToken());
		verify(jdbcTemplate).update(contains("attempt_count=?"), eq(claimed.get(0).leaseToken()), eq("job-1"), eq(0));
		verify(jdbcTemplate).update(contains("attempt_count=?"), eq(claimed.get(1).leaseToken()), eq("job-2"), eq(2));
	}

	@Test
	void deliveryCompletionUsesClaimTokenAndExpectedAttemptCount() {
		DeliveryJob job = new DeliveryJob("job-1", "event-1", "source-1", "FEEDBACK_UPSTREAM", "STANDARD",
				"callback-1", 2, null, "claim-token");

		repository.markDeliverySuccess(job, 200);

		verify(jdbcTemplate).update(contains("AND attempt_count=?"), eq(3), eq("job-1"), eq("claim-token"), eq(2));
	}

	@Test
	void unclaimedDeliveryCannotWriteAResult() {
		DeliveryJob job = new DeliveryJob("job-1", "event-1", "source-1", "FEEDBACK_UPSTREAM", "STANDARD",
				"callback-1", 0);

		assertThatThrownBy(() -> repository.markDeliverySuccess(job, 200))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("claim lease token");
		verify(jdbcTemplate, never()).update(contains("SET status='SUCCESS'"), any(Object[].class));
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

	private RouteSnapshot frozenRoute() {
		return route().withFactDimensions("SCX", "product-1", "task-1", "media-account-1", "operator-1",
				"route-version-1");
	}

	private RouteEventSnapshot eventMapping(int dailyLimit) {
		return new RouteEventSnapshot("mapping-1", "ACTIVATE", "激活", "ACTIVATE", "激活", "MEDIA_ACTIVATE", "媒体激活",
				new BigDecimal("2.500000"), dailyLimit);
	}

	private RouteSnapshot versionedRoute(RouteEventSnapshot eventMapping) {
		RouteSnapshot route = route();
		return new RouteSnapshot(route.routeId(), route.bizId(), route.routeVersion(), route.channelId(),
				route.channelCode(), route.channelAdapterCode(), route.channelAuthType(), route.channelKeyVersion(),
				route.channelSecret(), route.advertiserId(), route.appLinkId(), route.upstreamAdapterCode(),
				route.upstreamSecret(), route.bidUrl(), route.upstreamRequiredMacros(), route.downstreamCallbackUrl(),
				route.unitPrice(), route.deductionRatio(), route.rtaMode(), route.billingMode(),
				route.clickDedupWindowSeconds(), List.of(eventMapping))
			.withFactDimensions("SCX", "product-1", "task-1", "media-account-1", "operator-1", "route-version-1");
	}

}
