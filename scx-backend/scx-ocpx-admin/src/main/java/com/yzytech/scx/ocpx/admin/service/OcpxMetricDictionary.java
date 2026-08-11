package com.yzytech.scx.ocpx.admin.service;

import com.yzytech.scx.ocpx.admin.dto.UnifiedReportViews;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

@Component
public class OcpxMetricDictionary {

	public static final String VERSION = "S8_V1";

	private static final Set<String> BASE = Set.of("impressions", "rawClicks", "validClicks", "duplicateClicks",
			"activations", "registrations", "payments", "orders", "costAmount", "adjustmentCount", "deductionCount");

	private static final Set<String> RATIOS = Set.of("activationRate", "registrationRate", "paymentRate",
			"registrationPaymentRate");

	public Set<String> allowed() {
		return java.util.stream.Stream.concat(BASE.stream(), RATIOS.stream())
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public List<UnifiedReportViews.MetricDefinition> definitions(boolean dailyTaskOrLink, boolean sensitive) {
		List<UnifiedReportViews.MetricDefinition> definitions = new java.util.ArrayList<>();
		definitions
			.add(new UnifiedReportViews.MetricDefinition("impressions", "曝光", "COUNT", null, null, "AVAILABLE", false));
		definitions
			.add(new UnifiedReportViews.MetricDefinition("rawClicks", "原始点击", "COUNT", null, null, "AVAILABLE", false));
		definitions.add(new UnifiedReportViews.MetricDefinition("validClicks", "有效点击", "COUNT", null, null, "AVAILABLE",
				false));
		definitions.add(new UnifiedReportViews.MetricDefinition("duplicateClicks", "重复点击", "COUNT", null, null,
				"AVAILABLE", false));
		definitions
			.add(new UnifiedReportViews.MetricDefinition("activations", "激活", "COUNT", null, null, "AVAILABLE", false));
		definitions.add(new UnifiedReportViews.MetricDefinition("registrations", "注册", "COUNT", null, null, "AVAILABLE",
				false));
		definitions
			.add(new UnifiedReportViews.MetricDefinition("payments", "付费", "COUNT", null, null, "AVAILABLE", false));
		definitions.add(new UnifiedReportViews.MetricDefinition("activationRate", "激活率", "RATIO", "activations",
				"validClicks", "AVAILABLE", false));
		definitions.add(new UnifiedReportViews.MetricDefinition("registrationRate", "注册率", "RATIO", "registrations",
				"validClicks", "AVAILABLE", false));
		definitions.add(new UnifiedReportViews.MetricDefinition("paymentRate", "付费率", "RATIO", "payments",
				"activations", "AVAILABLE", false));
		if (dailyTaskOrLink) {
			definitions.add(new UnifiedReportViews.MetricDefinition("registrationPaymentRate", "注册付费率", "RATIO",
					"payments", "registrations", "AVAILABLE", false));
		}
		if (sensitive) {
			definitions.add(new UnifiedReportViews.MetricDefinition("costAmount", "消耗", "AMOUNT", null, null,
					"AVAILABLE", true));
		}
		definitions.add(new UnifiedReportViews.MetricDefinition("unavailableDenominatorMetric", "未锁定指标", "RATIO", null,
				null, "UNAVAILABLE", false));
		return List.copyOf(definitions);
	}

	public Map<String, UnifiedReportViews.MetricValue> select(Map<String, BigDecimal> base, Set<String> requested,
			boolean dailyTaskOrLink, boolean sensitive) {
		Map<String, UnifiedReportViews.MetricValue> values = new LinkedHashMap<>();
		for (String metric : requested) {
			if (BASE.contains(metric)) {
				if (!"costAmount".equals(metric) || sensitive) {
					values.put(metric, new UnifiedReportViews.MetricValue(base.getOrDefault(metric, BigDecimal.ZERO),
							null, null, "AVAILABLE"));
				}
				continue;
			}
			UnifiedReportViews.MetricValue ratio = switch (metric) {
				case "activationRate" -> ratio(base, "activations", "validClicks");
				case "registrationRate" -> ratio(base, "registrations", "validClicks");
				case "paymentRate" -> ratio(base, "payments", "activations");
				case "registrationPaymentRate" -> dailyTaskOrLink ? ratio(base, "payments", "registrations")
						: new UnifiedReportViews.MetricValue(null, null, null, "UNAVAILABLE");
				default -> new UnifiedReportViews.MetricValue(null, null, null, "UNAVAILABLE");
			};
			values.put(metric, ratio);
		}
		return Collections.unmodifiableMap(values);
	}

	public BigDecimal comparisonChange(UnifiedReportViews.MetricValue current,
			UnifiedReportViews.MetricValue comparison) {
		if (current == null || comparison == null || current.value() == null || comparison.value() == null
				|| comparison.value().signum() == 0) {
			return null;
		}
		return current.value()
			.subtract(comparison.value())
			.multiply(BigDecimal.valueOf(100))
			.divide(comparison.value().abs(), 4, RoundingMode.HALF_UP);
	}

	private UnifiedReportViews.MetricValue ratio(Map<String, BigDecimal> base, String numerator, String denominator) {
		BigDecimal dividend = base.getOrDefault(numerator, BigDecimal.ZERO);
		BigDecimal divisor = base.getOrDefault(denominator, BigDecimal.ZERO);
		if (divisor.signum() == 0) {
			return new UnifiedReportViews.MetricValue(null, dividend, divisor, "ZERO_DENOMINATOR");
		}
		BigDecimal value = dividend.multiply(BigDecimal.valueOf(100)).divide(divisor, 4, RoundingMode.HALF_UP);
		return new UnifiedReportViews.MetricValue(value, dividend, divisor, "AVAILABLE");
	}

}
