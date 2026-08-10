package com.yzytech.scx.adx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzytech.scx.adx.config.AdxReportingProperties;
import com.yzytech.scx.adx.dto.DashboardQuery;
import com.yzytech.scx.adx.dto.UnifiedReportQuery;
import com.yzytech.scx.adx.dto.UnifiedReportViews;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class AdxDashboardServiceTest {

	private final AdxUnifiedReportService reportService = mock(AdxUnifiedReportService.class);

	private final AdxDashboardCache cache = mock(AdxDashboardCache.class);

	private final AdxReportingProperties properties = new AdxReportingProperties();

	private final AdxMetricDictionary dictionary = new AdxMetricDictionary();

	private final AdxDashboardService service = new AdxDashboardService(reportService, dictionary, cache, properties);

	@BeforeEach
	void prepare() {
		when(cache.get(any())).thenReturn(java.util.Optional.empty());
	}

	@Test
	void reusesUnifiedPagesForCardsTrendRankingsAndSafeFreshness() {
		AdxUnifiedReportService.DashboardSession session = session(7, "scope-a", LocalDateTime.of(2026, 8, 10, 12, 0));
		when(reportService.openDashboardSession(any())).thenReturn(session);
		when(reportService.queryForDashboard(any(), eq(session))).thenAnswer(invocation -> {
			UnifiedReportQuery query = invocation.getArgument(0);
			if (query.endDate().isBefore(LocalDate.of(2026, 8, 1))) {
				return page(query.groupBy(), "previous", "FINAL", LocalDateTime.of(2026, 7, 31, 23, 0), 5, 1);
			}
			if ("PRODUCT".equals(query.groupBy())) {
				return multiDayProductPage();
			}
			String freshness = "TASK".equals(query.groupBy()) ? "DELAYED" : "PROVISIONAL";
			return page(query.groupBy(), query.groupBy().toLowerCase(), freshness,
					LocalDateTime.of(2026, 8, 10, "TASK".equals(query.groupBy()) ? 9 : 10, 0), 10, 1);
		});

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("user-a"));
			var result = service.overview(query());

			assertThat(result.cards().status()).isEqualTo("READY");
			assertThat(result.cards().data()).extracting(card -> card.metric())
				.containsExactly("validClicks", "activations", "activationRate", "registrations", "payments");
			assertThat(result.cards().data().get(2).current().value()).isEqualByComparingTo("50.0000");
			assertThat(result.trend().status()).isEqualTo("READY");
			assertThat(result.productRanking().data().records().get(0).objectId()).isEqualTo("product");
			assertThat(result.productRanking().data().records().get(0).metrics().get("validClicks").value())
				.isEqualByComparingTo("30");
			assertThat(result.productRanking().data().records().get(0).metrics().get("activationRate").value())
				.isEqualByComparingTo("33.3333");
			assertThat(result.taskRanking().data().records().get(0).objectId()).isEqualTo("task");
			assertThat(result.mediaRanking().data().records().get(0).objectId()).isEqualTo("media");
			assertThat(result.asOf()).isEqualTo(LocalDateTime.of(2026, 8, 10, 9, 0));
			assertThat(result.freshnessStatus()).isEqualTo("DELAYED");
			assertThat(result.scopeRevision()).isEqualTo(7);
			assertThat(result.partialFailure()).isFalse();
			verify(cache).put(any(), eq(result), eq(45));
		}
	}

	@Test
	void cacheIsIsolatedByUserAndNaturallyBypassesOnScopeRevisionOrWatermark() {
		AdxUnifiedReportService.DashboardSession first = session(3, "scope-a", LocalDateTime.of(2026, 8, 10, 10, 0));
		AdxUnifiedReportService.DashboardSession changed = session(4, "scope-b", LocalDateTime.of(2026, 8, 10, 11, 0));
		AdxUnifiedReportService.DashboardSession watermarkChanged = session(4, "scope-b",
				LocalDateTime.of(2026, 8, 10, 12, 0));
		when(reportService.openDashboardSession(any())).thenReturn(first, first, changed, watermarkChanged);
		when(reportService.queryForDashboard(any(), any())).thenAnswer(invocation -> {
			UnifiedReportQuery query = invocation.getArgument(0);
			return page(query.groupBy(), query.groupBy().toLowerCase(), "FINAL", LocalDateTime.of(2026, 8, 10, 10, 0),
					10, 1);
		});

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser)
				.thenReturn(user("user-a"), user("user-b"), user("user-a"), user("user-a"));
			service.overview(query());
			service.overview(query());
			service.overview(query());
			service.overview(query());
			verify(reportService, times(24)).queryForDashboard(any(), any());
			ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
			verify(cache, times(4)).put(keys.capture(), any(), eq(45));
			assertThat(keys.getAllValues()).doesNotHaveDuplicates();
		}
	}

	@Test
	void cacheFailureFallsBackAndOneRankingFailureDoesNotClearSuccessfulSections() {
		AdxUnifiedReportService.DashboardSession session = session(1, "scope", LocalDateTime.of(2026, 8, 10, 10, 0));
		when(reportService.openDashboardSession(any())).thenReturn(session);
		when(cache.get(any())).thenThrow(new IllegalStateException("cache unavailable"));
		doThrow(new IllegalStateException("cache unavailable")).when(cache).put(any(), any(), eq(45));
		when(reportService.queryForDashboard(any(), eq(session))).thenAnswer(invocation -> {
			UnifiedReportQuery query = invocation.getArgument(0);
			if ("PRODUCT".equals(query.groupBy())) {
				throw new IllegalStateException("section failed");
			}
			return page(query.groupBy(), query.groupBy().toLowerCase(), "FINAL", LocalDateTime.of(2026, 8, 10, 10, 0),
					10, 1);
		});

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(user("user-a"));
			var result = service.overview(query());
			assertThat(result.cards().status()).isEqualTo("READY");
			assertThat(result.trend().status()).isEqualTo("READY");
			assertThat(result.productRanking().status()).isEqualTo("ERROR");
			assertThat(result.productRanking().data()).isNull();
			assertThat(result.productRanking().traceId()).isNotBlank();
			assertThat(result.taskRanking().data().records()).isNotEmpty();
			assertThat(result.partialFailure()).isTrue();
			verify(cache, never()).put(any(), any(), any(Integer.class));
		}
	}

	@Test
	void rejectsInvalidRangeBeforeOpeningScopeOrCache() {
		assertThatThrownBy(() -> service.overview(new DashboardQuery(LocalDate.of(2026, 8, 10),
				LocalDate.of(2026, 8, 1), null, null, null, null, null, null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("开始日期");
		verify(reportService, never()).openDashboardSession(any());
	}

	private DashboardQuery query() {
		return new DashboardQuery(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), "adv-1", null, null, null,
				"channel-1", "media-1");
	}

	private AdxUnifiedReportService.DashboardSession session(long revision, String scopeHash, LocalDateTime watermark) {
		AdxAccessScope.Restriction all = new AdxAccessScope.Restriction(true, List.of());
		return new AdxUnifiedReportService.DashboardSession(new AdxUnifiedReportService.FrozenScope(all, all, all),
				revision, scopeHash, watermark, false, LocalDate.of(2026, 7, 22), LocalDate.of(2026, 8, 10));
	}

	private UnifiedReportViews.Page page(String groupBy, String objectId, String freshness, LocalDateTime asOf,
			int validClicks, long total) {
		Map<String, UnifiedReportViews.MetricValue> metrics = metrics(validClicks);
		Map<String, Object> dimensions = "TIME".equals(groupBy)
				? Map.of("bucket", "2026-08-10", "grain", "DAY", "key", "2026-08-10")
				: Map.of("bucket", "2026-08-10", "grain", "DAY", "key", "2026-08-10|" + objectId, "objectId", objectId);
		List<UnifiedReportViews.Record> records = total == 0 ? List.of()
				: List.of(new UnifiedReportViews.Record("2026-08-10|" + objectId, groupBy, dimensions, metrics,
						"COMPLETE", freshness, asOf));
		List<UnifiedReportViews.MetricDefinition> definitions = dictionary.definitions(false, false);
		return new UnifiedReportViews.Page(records, 1, 200, total, new UnifiedReportViews.Summary(metrics, "page"),
				new UnifiedReportViews.Summary(metrics, "query"), asOf, "Asia/Shanghai", freshness,
				AdxMetricDictionary.VERSION, definitions);
	}

	private UnifiedReportViews.Page multiDayProductPage() {
		Map<String, UnifiedReportViews.MetricValue> first = metrics(10);
		Map<String, UnifiedReportViews.MetricValue> second = metrics(20);
		List<UnifiedReportViews.Record> records = List.of(
				new UnifiedReportViews.Record("2026-08-09|product", "PRODUCT",
						Map.of("bucket", "2026-08-09", "objectId", "product"), first, "COMPLETE", "FINAL",
						LocalDateTime.of(2026, 8, 10, 9, 30)),
				new UnifiedReportViews.Record("2026-08-10|product", "PRODUCT",
						Map.of("bucket", "2026-08-10", "objectId", "product"), second, "COMPLETE", "PROVISIONAL",
						LocalDateTime.of(2026, 8, 10, 10, 0)));
		return new UnifiedReportViews.Page(records, 1, 200, 2, new UnifiedReportViews.Summary(first, "page"),
				new UnifiedReportViews.Summary(first, "query"), LocalDateTime.of(2026, 8, 10, 10, 0), "Asia/Shanghai",
				"PROVISIONAL", AdxMetricDictionary.VERSION, dictionary.definitions(false, false));
	}

	private Map<String, UnifiedReportViews.MetricValue> metrics(int validClicks) {
		return Map.ofEntries(Map.entry("impressions", value(20)), Map.entry("validClicks", value(validClicks)),
				Map.entry("activations", value(5)), Map.entry("registrations", value(4)),
				Map.entry("payments", value(2)), Map.entry("activationRate", ratio(5, validClicks)),
				Map.entry("registrationRate", ratio(4, validClicks)), Map.entry("paymentRate", ratio(2, 5)));
	}

	private UnifiedReportViews.MetricValue value(int value) {
		return new UnifiedReportViews.MetricValue(BigDecimal.valueOf(value), null, null, "AVAILABLE");
	}

	private UnifiedReportViews.MetricValue ratio(int numerator, int denominator) {
		if (denominator == 0) {
			return new UnifiedReportViews.MetricValue(null, BigDecimal.valueOf(numerator), BigDecimal.ZERO,
					"ZERO_DENOMINATOR");
		}
		return new UnifiedReportViews.MetricValue(
				BigDecimal.valueOf(numerator)
					.multiply(BigDecimal.valueOf(100))
					.divide(BigDecimal.valueOf(denominator), 4, java.math.RoundingMode.HALF_UP),
				BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator), "AVAILABLE");
	}

	private BootUser user(String id) {
		BootUser user = new BootUser();
		user.setUserId(id);
		return user;
	}

}
